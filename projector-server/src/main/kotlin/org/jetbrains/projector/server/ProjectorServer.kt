/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2020 JetBrains s.r.o.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.server

import org.java_websocket.WebSocket
import org.java_websocket.WebSocketServerFactory
import org.java_websocket.exceptions.WebsocketNotConnectedException
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.jetbrains.projector.awt.PClipboard
import org.jetbrains.projector.awt.PToolkit
import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.font.PFontManager
import org.jetbrains.projector.awt.image.PGraphicsDevice
import org.jetbrains.projector.awt.image.PGraphicsEnvironment
import org.jetbrains.projector.awt.image.PVolatileImage
import org.jetbrains.projector.awt.peer.PComponentPeer
import org.jetbrains.projector.awt.peer.PMouseInfoPeer
import org.jetbrains.projector.common.misc.Do
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.data.ImageData
import org.jetbrains.projector.common.protocol.data.ImageId
import org.jetbrains.projector.common.protocol.data.Point
import org.jetbrains.projector.common.protocol.handshake.COMMON_VERSION
import org.jetbrains.projector.common.protocol.handshake.ToClientHandshakeFailureEvent
import org.jetbrains.projector.common.protocol.handshake.ToClientHandshakeSuccessEvent
import org.jetbrains.projector.common.protocol.handshake.commonVersionList
import org.jetbrains.projector.common.protocol.toClient.*
import org.jetbrains.projector.common.protocol.toServer.*
import org.jetbrains.projector.server.ReadyClientSettings.TouchState
import org.jetbrains.projector.server.core.ProjectorHttpWsServer
import org.jetbrains.projector.server.core.convert.toAwt.toAwtKeyEvent
import org.jetbrains.projector.server.core.ij.md.IjInjectorAgentInitializer
import org.jetbrains.projector.server.core.ij.md.PanelUpdater
import org.jetbrains.projector.server.core.protocol.HandshakeTypesSelector
import org.jetbrains.projector.server.core.protocol.KotlinxJsonToClientHandshakeEncoder
import org.jetbrains.projector.server.core.protocol.KotlinxJsonToServerHandshakeDecoder
import org.jetbrains.projector.server.idea.CaretInfoUpdater
import org.jetbrains.projector.server.idea.IdeColors
import org.jetbrains.projector.server.idea.KeymapSetter
import org.jetbrains.projector.server.idea.SettingsInitializer
import org.jetbrains.projector.server.log.Logger
import org.jetbrains.projector.server.service.ProjectorAwtInitializer
import org.jetbrains.projector.server.service.ProjectorDrawEventQueue
import org.jetbrains.projector.server.service.ProjectorImageCacher
import org.jetbrains.projector.server.util.*
import sun.awt.AWTAccessor
import sun.font.FontManagerFactory
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.peer.ComponentPeer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import java.awt.Point as AwtPoint

class ProjectorServer private constructor(
  port: Int,
  private val laterInvokator: LaterInvokator,
  private val isAgent: Boolean,
) {

  private val httpWsServer = object : ProjectorHttpWsServer(port) {

    override fun onStart() {
      logger.info { "Server started on port $port" }

      updateThread = thread(isDaemon = true) {
        // TODO: remove this thread: encapsulate the logic in an extracted class and maybe even don't use threads but coroutines' channels
        logger.debug { "Daemon thread starts" }
        while (!Thread.currentThread().isInterrupted) {
          try {
            val dataToSend = createDataToSend()  // creating data even if there are no clients to avoid memory leaks

            sendPictures(dataToSend)

            Thread.sleep(10)
          }
          catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
          }
          catch (t: Throwable) {
            logger.error(t) { "Unhandled in daemon thread has happened" }
          }
        }
        logger.debug { "Daemon thread finishes" }
      }

      caretInfoUpdater.start()
    }

    override fun onWsMessage(connection: WebSocket, message: ByteBuffer) {
      throw RuntimeException("Unsupported message type: $message")
    }

    override fun onWsMessage(connection: WebSocket, message: String) {
      when (val clientSettings = connection.getAttachment<ClientSettings>()!!) {
        is ConnectedClientSettings -> setUpClient(connection, clientSettings, message)

        is SetUpClientSettings -> {
          // this means that the client has loaded fonts and is ready to draw

          connection.setAttachment(ReadyClientSettings(clientSettings.connectionMillis, clientSettings.setUpClientData))

          PVolatileImage.images.forEach(PVolatileImage::invalidate)
          PWindow.windows.forEach {
            SwingUtilities.invokeAndWait { it.target.revalidate() }  // this solves PRJ-69
          }
          PWindow.windows.forEach(PWindow::repaint)
          previousWindowEvents = emptySet()
          caretInfoUpdater.createCaretInfoEvent()
          PanelUpdater.updateAll()
        }

        is ReadyClientSettings -> {
          val events = with(clientSettings.setUpClientData) {
            val decompressed = toServerMessageDecompressor.decompress(message)
            toServerMessageDecoder.decode(decompressed)
          }

          events.forEach { processMessage(clientSettings, it) }
        }
      }
    }

    override fun onWsClose(connection: WebSocket) {
      // todo: we need more informative message, add parameters to this method inside the superclass
      logger.info { "${connection.remoteSocketAddress?.address?.hostAddress} disconnected." }
    }

    override fun onWsOpen(connection: WebSocket) {
      connection.setAttachment(ConnectedClientSettings(connectionMillis = System.currentTimeMillis()))
      logger.info { "${connection.remoteSocketAddress.address.hostAddress} connected." }
    }

    override fun onError(connection: WebSocket?, e: Exception) {
      logger.error(e) { "onError" }
    }

    override fun getMainWindows(): List<MainWindow> = ProjectorServer.getMainWindows().map {
      MainWindow(
        title = it.title,
        pngBase64Icon = it.icons
          ?.firstOrNull()
          ?.let { imageId -> ProjectorImageCacher.getImage(imageId as ImageId) as? ImageData.PngBase64 }
          ?.pngBase64,
      )
    }
  }

  private lateinit var updateThread: Thread

  private val caretInfoQueue = ConcurrentLinkedQueue<ServerCaretInfoChangedEvent.CaretInfoChange>()

  private val caretInfoUpdater = CaretInfoUpdater { caretInfo ->
    caretInfoQueue.add(caretInfo)
  }

  private val markdownQueue = ConcurrentLinkedQueue<ServerMarkdownEvent>()

  private var windowColorsEvent: ServerWindowColorsEvent? = null

  private val ideaColors = IdeColors { colors ->
    windowColorsEvent = ServerWindowColorsEvent(colors)
  }

  init {
    PanelUpdater.showCallback = { id, show ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownShowEvent(id, show))
    }
    PanelUpdater.resizeCallback = { id, size ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownResizeEvent(id, size.toCommonIntSize()))
    }
    PanelUpdater.moveCallback = { id, point ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownMoveEvent(id, point.shift(PGraphicsDevice.clientShift)))
    }
    PanelUpdater.disposeCallback = { id ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownDisposeEvent(id))
    }
    PanelUpdater.placeToWindowCallback = { id, rootComponent ->
      rootComponent?.let {
        val peer = AWTAccessor.getComponentAccessor().getPeer<ComponentPeer>(it)

        if (peer !is PComponentPeer) {
          return@let
        }

        markdownQueue.add(ServerMarkdownEvent.ServerMarkdownPlaceToWindowEvent(id, peer.pWindow.id))
      }
    }
    PanelUpdater.setHtmlCallback = { id, html ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownSetHtmlEvent(id, html))
    }
    PanelUpdater.setCssCallback = { id, css ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownSetCssEvent(id, css))
    }
    PanelUpdater.scrollCallback = { id, offset ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownScrollEvent(id, offset))
    }
    PanelUpdater.browseUriCallback = { link ->
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownBrowseUriEvent(link))
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun createDataToSend(): List<ServerEvent> {
    val clipboardEvent = when (val clipboardContents = PClipboard.extractLastContents()) {
      null -> emptyList()

      else -> when (clipboardContents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        false -> emptyList()

        true -> listOf(ServerClipboardEvent(clipboardContents.getTransferData(DataFlavor.stringFlavor) as String))
      }
    }

    calculateMainWindowShift()

    val drawCommands = ProjectorDrawEventQueue.getQueues()
      .filter { it.commands.isNotEmpty() }
      .map { queue ->
        val drawEvents: List<List<ServerWindowEvent>> = extractData(queue.commands)

        ServerDrawCommandsEvent(
          target = queue.target,
          drawEvents = drawEvents.convertToSimpleList()
        )
      }

    val windows = PWindow.windows
      .mapIndexed { i, window ->
        WindowData(
          id = window.id,
          title = window.title,
          icons = window.icons?.map { it as ImageId },
          isShowing = window.target.isShowing,
          zOrder = i,
          bounds = window.target.shiftBounds(PGraphicsDevice.clientShift),
          headerHeight = window.headerHeight,
          cursorType = window.cursor?.type?.toCursorType(),
          resizable = window.resizable,
          modal = window.modal,
          undecorated = window.undecorated,
          windowType = window.windowType
        )
      }

    val windowSetChangedEvent = when {
      areChangedWindows(windows) -> listOf(ServerWindowSetChangedEvent(windows))
      else -> emptyList()
    }

    val newImagesCopy = extractData(ProjectorImageCacher.newImages)

    val caretInfoEvents = extractData(caretInfoQueue).map(::ServerCaretInfoChangedEvent)

    val markdownEvents = extractData(markdownQueue)

    val commandsCount = caretInfoEvents.size +
                        newImagesCopy.size + clipboardEvent.size + drawCommands.size + windowSetChangedEvent.size + markdownEvents.size + 1

    val allEvents = buildList(commandsCount) {
      addAll(caretInfoEvents)
      addAll(newImagesCopy)
      addAll(clipboardEvent)
      addAll(drawCommands)
      addAll(windowSetChangedEvent)
      addAll(markdownEvents)
      windowColorsEvent?.let { add(it); windowColorsEvent = null }
    }

    ProjectorImageCacher.collectGarbage()

    return allEvents
  }

  private fun processMessage(clientSettings: ReadyClientSettings, message: ClientEvent) {
    if (
      !clientSettings.setUpClientData.hasWriteAccess &&
      message !is ClientRequestImageDataEvent &&
      message !is ClientRequestPingEvent
    ) {
      return
    }

    Do exhaustive when (message) {
      is ClientResizeEvent -> SwingUtilities.invokeLater { resize(message.size.width, message.size.height) }

      is ClientMouseEvent -> SwingUtilities.invokeLater {
        val shiftedMessage = message.shift(PGraphicsDevice.clientShift)

        PMouseInfoPeer.lastMouseCoords.setLocation(shiftedMessage.x, shiftedMessage.y)

        val window = PWindow.getWindow(message.windowId)?.target
        PMouseInfoPeer.lastWindowUnderMouse = window

        window ?: return@invokeLater

        fun isEnoughDeltaForScrolling(previousTouchState: TouchState.Scrolling, newX: Int, newY: Int): Boolean {
          // reduce number of scroll events to make deltas bigger.
          // this helps when to generate proper MouseWheelEvents with correct transformation of pixels to scroll units

          return (newX - previousTouchState.lastX).absoluteValue > PIXEL_DELTA_ENOUGH_FOR_SCROLLING ||
                 (newY - previousTouchState.lastY).absoluteValue > PIXEL_DELTA_ENOUGH_FOR_SCROLLING
        }

        val newTouchState = when (shiftedMessage.mouseEventType) {
          ClientMouseEvent.MouseEventType.UP -> TouchState.Released
          ClientMouseEvent.MouseEventType.DOWN -> TouchState.OnlyPressed(message.timeStamp, shiftedMessage.x, shiftedMessage.y)
          ClientMouseEvent.MouseEventType.TOUCH_DRAG -> when (val touchState = clientSettings.touchState) {
            is TouchState.Scrolling -> when (isEnoughDeltaForScrolling(touchState, shiftedMessage.x, shiftedMessage.y)) {
              true -> TouchState.Scrolling(touchState.initialX, touchState.initialY, shiftedMessage.x, shiftedMessage.y)
              false -> return@invokeLater
            }
            is TouchState.Dragging -> TouchState.Dragging
            is TouchState.OnlyPressed -> when (touchState.connectionMillis + 500 < shiftedMessage.timeStamp) {
              true -> TouchState.Dragging
              false -> TouchState.Scrolling(touchState.lastX, touchState.lastY, shiftedMessage.x, shiftedMessage.y)
            }
            is TouchState.Released -> TouchState.Released  // drag events shouldn't come when touch is not pressing so let's skip it
          }
          else -> clientSettings.touchState
        }
        val mouseEvent = createMouseEvent(window, shiftedMessage, clientSettings.touchState, newTouchState, clientSettings.connectionMillis)
        clientSettings.touchState = newTouchState
        laterInvokator(mouseEvent)
      }

      is ClientWheelEvent -> SwingUtilities.invokeLater {
        val shiftedMessage = message.shift(PGraphicsDevice.clientShift)
        PMouseInfoPeer.lastMouseCoords.setLocation(shiftedMessage.x, shiftedMessage.y)

        val window = PWindow.getWindow(message.windowId)?.target
        PMouseInfoPeer.lastWindowUnderMouse = window

        window ?: return@invokeLater

        val mouseWheelEvent = createMouseWheelEvent(window, shiftedMessage, clientSettings.connectionMillis)
        laterInvokator(mouseWheelEvent)
      }

      is ClientKeyEvent -> message.toAwtKeyEvent(
        connectionMillis = clientSettings.connectionMillis,
        target = focusOwnerOrTarget(PWindow.windows.last().target),
        errorLogger = { logger.error(lazyMessage = it) }
      )
        ?.let {
          SwingUtilities.invokeLater {
            laterInvokator(it)
          }
        }

      is ClientKeyPressEvent -> message.toAwtKeyEvent(
        connectionMillis = clientSettings.connectionMillis,
        target = focusOwnerOrTarget(PWindow.windows.last().target),
        errorLogger = { logger.error(lazyMessage = it) }
      )
        ?.let {
          SwingUtilities.invokeLater {
            laterInvokator(it)
          }
        }

      is ClientRequestImageDataEvent -> {
        val imageData = ProjectorImageCacher.getImage(message.imageId) ?: ImageData.Empty

        val resource = ServerImageDataReplyEvent(message.imageId, imageData)
        clientSettings.requestedData.add(resource)

        Unit
      }

      is ClientClipboardEvent -> {
        val transferable = object : Transferable {

          override fun getTransferData(flavor: DataFlavor?): Any? {
            if (!isDataFlavorSupported(flavor)) {
              throw UnsupportedFlavorException(flavor)
            }

            return message.stringContent
          }

          override fun isDataFlavorSupported(flavor: DataFlavor?) = flavor in transferDataFlavors

          override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)
        }

        SwingUtilities.invokeLater {
          PClipboard.putContents(transferable)
        }
      }

      is ClientRequestPingEvent -> {
        val pingReply = ServerPingReplyEvent(
          clientTimeStamp = message.clientTimeStamp,
          serverReadEventTimeStamp = (System.currentTimeMillis() - clientSettings.connectionMillis).toInt()
        )

        clientSettings.requestedData.add(pingReply)
      }

      is ClientOpenLinkEvent -> PanelUpdater.openInExternalBrowser(message.link)

      is ClientSetKeymapEvent -> if (isAgent) {
        logger.info { "Client keymap was ignored (agent mode)!" }
      }
      else if (getProperty(ENABLE_AUTO_KEYMAP_SETTING)?.toBoolean() == false) {
        logger.info { "Client keymap was ignored (property specified)!" }
      }
      else {
        KeymapSetter.setKeymap(message.keymap)
      }

      is ClientWindowMoveEvent -> {
        SwingUtilities.invokeLater { PWindow.getWindow(message.windowId)?.apply { move(message.deltaX, message.deltaY) } }
      }

      is ClientWindowResizeEvent -> {
        SwingUtilities.invokeLater {
          PWindow.getWindow(message.windowId)?.apply { this.resize(message.deltaX, message.deltaY, message.direction.toDirection()) }
        }
      }

      is ClientWindowCloseEvent -> SwingUtilities.invokeLater { PWindow.getWindow(message.windowId)?.close() }
    }
  }

  private fun setUpClient(conn: WebSocket, connectedClientSettings: ConnectedClientSettings, message: String) {
    fun sendHandshakeFailureEvent(reason: String) {
      val failureEvent = ToClientHandshakeFailureEvent(reason)

      conn.send(KotlinxJsonToClientHandshakeEncoder.encode(failureEvent))
    }

    val toServerHandshakeEvent = KotlinxJsonToServerHandshakeDecoder.decode(message)

    val hasWriteAccess = when (toServerHandshakeEvent.token) {
      getProperty(TOKEN_ENV_NAME) -> true
      getProperty(RO_TOKEN_ENV_NAME) -> false
      else -> {
        sendHandshakeFailureEvent("Bad handshake token")
        return
      }
    }

    if (toServerHandshakeEvent.commonVersion != COMMON_VERSION) {
      val reason =
        "Incompatible common protocol versions: server - $COMMON_VERSION (#${commonVersionList.indexOf(COMMON_VERSION)}), " +
        "client - ${toServerHandshakeEvent.commonVersion} (#${toServerHandshakeEvent.commonVersionId})"
      sendHandshakeFailureEvent(reason)

      return
    }

    val toClientCompressor = HandshakeTypesSelector.selectToClientCompressor(toServerHandshakeEvent.supportedToClientCompressions)
    if (toClientCompressor == null) {
      sendHandshakeFailureEvent(
        "Server doesn't support any of the following to-client compressions: ${toServerHandshakeEvent.supportedToClientCompressions}"
      )

      return
    }

    val toClientEncoder = HandshakeTypesSelector.selectToClientEncoder(toServerHandshakeEvent.supportedToClientProtocols)
    if (toClientEncoder == null) {
      sendHandshakeFailureEvent(
        "Server doesn't support any of the following to-client protocols: ${toServerHandshakeEvent.supportedToClientProtocols}"
      )

      return
    }

    val toServerDecompressor = HandshakeTypesSelector.selectToServerDecompressor(toServerHandshakeEvent.supportedToServerCompressions)
    if (toServerDecompressor == null) {
      sendHandshakeFailureEvent(
        "Server doesn't support any of the following to-server compressions: ${toServerHandshakeEvent.supportedToServerCompressions}"
      )

      return
    }

    val toServerDecoder = HandshakeTypesSelector.selectToServerDecoder(toServerHandshakeEvent.supportedToServerProtocols)
    if (toServerDecoder == null) {
      sendHandshakeFailureEvent(
        "Server doesn't support any of the following to-server protocols: ${toServerHandshakeEvent.supportedToServerProtocols}"
      )

      return
    }

    val ipString = conn.remoteSocketAddress?.address?.hostAddress

    if (
      isAgent &&
      getProperty(ENABLE_CONNECTION_CONFIRMATION)?.toBoolean() != false &&
      ipString !in setOf("127.0.0.1", "0:0:0:0:0:0:0:1")
    ) {
      logger.info { "Asking for connection confirmation because of agent mode..." }

      var selectedOption = -1

      SwingUtilities.invokeAndWait {
        val accessType = when (hasWriteAccess) {
          true -> "read-write"
          false -> "read-only"
        }

        selectedOption = JOptionPane.showOptionDialog(
          null,
          "Somebody ($ipString) wants to connect with $accessType access. Allow the connection?",
          "New connection",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE,
          null,
          null,
          null,
        )
      }

      if (selectedOption != 0) {  // 0=yes
        logger.info { "User has disallowed this connection..." }
        sendHandshakeFailureEvent("Other user has disallowed this connection.")
        return
      }
      logger.info { "User has allowed this connection..." }
    }

    val successEvent = ToClientHandshakeSuccessEvent(
      toClientCompression = toClientCompressor.compressionType,
      toClientProtocol = toClientEncoder.protocolType,
      toServerCompression = toServerDecompressor.compressionType,
      toServerProtocol = toServerDecoder.protocolType,
      fontDataHolders = PFontManager
        .allInstalledFonts
        .mapNotNull { font -> FontCacher.getId(font) }
        .map { fontId -> FontCacher.getFontData(fontId) },
      colors = ideaColors.colors
    )
    conn.send(KotlinxJsonToClientHandshakeEncoder.encode(successEvent))

    conn.setAttachment(
      SetUpClientSettings(
        connectionMillis = connectedClientSettings.connectionMillis,
        setUpClientData = SetUpClientData(
          hasWriteAccess = hasWriteAccess,
          toClientMessageEncoder = toClientEncoder,
          toClientMessageCompressor = toClientCompressor,
          toServerMessageDecoder = toServerDecoder,
          toServerMessageDecompressor = toServerDecompressor
        )
      )
    )

    if (hasWriteAccess) {
      with(toServerHandshakeEvent.initialSize) { resize(width, height) }
    }
  }

  private fun sendPictures(dataToSend: List<ServerEvent>) {
    httpWsServer.forEachOpenedConnection { client ->
      val readyClientSettings = client.getAttachment<ClientSettings?>() as? ReadyClientSettings ?: return@forEachOpenedConnection

      val compressed = with(readyClientSettings.setUpClientData) {
        val requestedData = extractData(readyClientSettings.requestedData)
        val message = requestedData + dataToSend

        if (message.isEmpty()) {
          return@forEachOpenedConnection
        }

        val encoded = toClientMessageEncoder.encode(message)
        toClientMessageCompressor.compress(encoded)
      }

      try {
        client.send(compressed)  // can cause a "disconnected already" exception
      }
      catch (e: WebsocketNotConnectedException) {
        logger.debug(e) { "While generating message, client disconnected" }
      }
    }
  }

  private var previousWindowEvents: Set<WindowData> = emptySet()

  private fun areChangedWindows(windowEvents: List<WindowData>): Boolean {
    val set = windowEvents.toSet()
    val hasDifferentWindowEvents = set != previousWindowEvents

    if (hasDifferentWindowEvents) {
      previousWindowEvents = set

      return true
    }

    return false
  }

  fun start() {
    httpWsServer.start()
  }

  @JvmOverloads
  fun stop(timeout: Int = 0) {
    httpWsServer.stop(timeout)

    if (::updateThread.isInitialized) {
      updateThread.interrupt()
    }

    caretInfoUpdater.stop()
  }

  companion object {

    private val logger = Logger(ProjectorServer::class.simpleName!!)

    private const val DEFAULT_SCROLL_AMOUNT = 1

    // todo: 100 is just a random but reasonable number;
    //       need to calculate this number from the context,
    //       maybe use the client's scaling ratio
    private const val PIXEL_PER_UNIT = 100

    // todo: 3 is a wild guess (scaling factor of mobile devices), need to get this number from the context
    private const val TOUCH_PIXEL_PER_UNIT = 3 * PIXEL_PER_UNIT
    private const val PIXEL_DELTA_ENOUGH_FOR_SCROLLING = 10

    @JvmStatic
    val isEnabled: Boolean
      get() = System.getProperty(ENABLE_PROPERTY_NAME)?.toBoolean() ?: false

    private fun getProperty(propName: String): String? {
      return System.getProperty(propName) ?: System.getenv(propName)
    }

    private fun focusOwnerOrTarget(target: Component): Component {
      val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
      return manager.focusOwner ?: target
    }

    private val mouseModifierMask = mapOf(
      MouseModifier.ALT_KEY to InputEvent.ALT_DOWN_MASK,
      MouseModifier.CTRL_KEY to InputEvent.CTRL_DOWN_MASK,
      MouseModifier.SHIFT_KEY to InputEvent.SHIFT_DOWN_MASK,
      MouseModifier.META_KEY to InputEvent.META_DOWN_MASK
    )

    private fun Set<MouseModifier>.toMouseInt(): Int {
      return map(mouseModifierMask::getValue).fold(0, Int::or)
    }

    private fun createMouseEvent(
      source: Component,
      event: ClientMouseEvent,
      previousTouchState: TouchState,
      newTouchState: TouchState,
      connectionMillis: Long,
    ): MouseEvent {
      val locationOnScreen = source.locationOnScreen

      val id = when (event.mouseEventType) {
        ClientMouseEvent.MouseEventType.MOVE -> MouseEvent.MOUSE_MOVED
        ClientMouseEvent.MouseEventType.DOWN -> MouseEvent.MOUSE_PRESSED
        ClientMouseEvent.MouseEventType.UP -> MouseEvent.MOUSE_RELEASED
        ClientMouseEvent.MouseEventType.CLICK -> MouseEvent.MOUSE_CLICKED
        ClientMouseEvent.MouseEventType.OUT -> MouseEvent.MOUSE_EXITED
        ClientMouseEvent.MouseEventType.DRAG -> MouseEvent.MOUSE_DRAGGED
        ClientMouseEvent.MouseEventType.TOUCH_DRAG -> {
          if (previousTouchState is TouchState.WithCoordinates && newTouchState is TouchState.Scrolling) {
            val deltaX = newTouchState.lastX - previousTouchState.lastX
            val deltaY = newTouchState.lastY - previousTouchState.lastY

            fun isHorizontal(): Boolean {
              return deltaX.absoluteValue > deltaY.absoluteValue
            }

            val (wheelDelta, modifiers) = if (isHorizontal()) {
              deltaX to (event.modifiers.toMouseInt() or InputEvent.SHIFT_DOWN_MASK)
            }
            else {
              deltaY to event.modifiers.toMouseInt()
            }

            val negatedWheelDelta = -wheelDelta  // touch scrolling is usually treated in reverse direction

            val normalizedWheelDelta = negatedWheelDelta.toDouble() / TOUCH_PIXEL_PER_UNIT
            val notNullNormalizedWheelDelta = roundToInfinity(normalizedWheelDelta).toInt()

            return MouseWheelEvent(
              source,
              MouseEvent.MOUSE_WHEEL, connectionMillis + event.timeStamp, modifiers,
              newTouchState.initialX - locationOnScreen.x, newTouchState.initialY - locationOnScreen.y,
              newTouchState.initialX - locationOnScreen.x, newTouchState.initialY - locationOnScreen.y, 0, false,
              MouseWheelEvent.WHEEL_UNIT_SCROLL, DEFAULT_SCROLL_AMOUNT, notNullNormalizedWheelDelta, normalizedWheelDelta
            )
          }

          MouseEvent.MOUSE_DRAGGED
        }
      }

      val awtEventButton = when (event.mouseEventType) {
        ClientMouseEvent.MouseEventType.MOVE,
        ClientMouseEvent.MouseEventType.OUT,
        -> MouseEvent.NOBUTTON

        else -> event.button + 1
      }

      val modifiers = event.modifiers.toMouseInt()
      val buttonModifier = if (awtEventButton == MouseEvent.NOBUTTON || event.mouseEventType == ClientMouseEvent.MouseEventType.UP) {
        0
      }
      else {
        InputEvent.getMaskForButton(awtEventButton)
      }

      val canTriggerPopup = awtEventButton == MouseEvent.BUTTON3

      return MouseEvent(
        source,
        id,
        connectionMillis + event.timeStamp,
        modifiers or buttonModifier,
        event.x - locationOnScreen.x, event.y - locationOnScreen.y,
        event.clickCount, canTriggerPopup, awtEventButton
      )
    }

    private fun createMouseWheelEvent(source: Component, event: ClientWheelEvent, connectionMillis: Long): MouseWheelEvent {
      fun isHorizontal(event: ClientWheelEvent): Boolean {
        return event.deltaX.absoluteValue > event.deltaY.absoluteValue
      }

      val (wheelDelta, modifiers) = if (isHorizontal(event)) {
        event.deltaX to (event.modifiers.toMouseInt() or InputEvent.SHIFT_DOWN_MASK)
      }
      else {
        event.deltaY to event.modifiers.toMouseInt()
      }

      val (mode, normalizedWheelDelta) = when (event.mode) {
        ClientWheelEvent.ScrollingMode.PIXEL -> MouseWheelEvent.WHEEL_UNIT_SCROLL to wheelDelta / PIXEL_PER_UNIT
        ClientWheelEvent.ScrollingMode.LINE -> MouseWheelEvent.WHEEL_UNIT_SCROLL to wheelDelta
        ClientWheelEvent.ScrollingMode.PAGE -> MouseWheelEvent.WHEEL_BLOCK_SCROLL to wheelDelta
      }
      val notNullNormalizedWheelDelta = roundToInfinity(normalizedWheelDelta).toInt()

      val locationOnScreen = source.locationOnScreen

      return MouseWheelEvent(
        source, MouseEvent.MOUSE_WHEEL, connectionMillis + event.timeStamp, modifiers,
        event.x - locationOnScreen.x, event.y - locationOnScreen.y,
        event.x - locationOnScreen.x, event.y - locationOnScreen.y, 0, false,
        mode, DEFAULT_SCROLL_AMOUNT, notNullNormalizedWheelDelta, normalizedWheelDelta
      )
    }

    private fun setupGraphicsEnvironment() {
      val classes = GraphicsEnvironment::class.java.declaredClasses
      val localGE = classes.single()
      check(localGE.name == "java.awt.GraphicsEnvironment\$LocalGE")

      localGE.getDeclaredField("INSTANCE").apply {
        unprotect()

        set(null, PGraphicsEnvironment())
      }
    }

    private fun setupToolkit() {
      Toolkit::class.java.getDeclaredField("toolkit").apply {
        unprotect()

        set(null, PToolkit())
      }
    }

    private fun setupFontManager() {
      FontManagerFactory::class.java.getDeclaredField("instance").apply {
        unprotect()

        set(null, PFontManager)
      }
    }

    private fun setupRepaintManager() {
      // todo: when we do smth w/ RepaintManager, IDEA crashes.
      //       Maybe it's because AppContext is used.
      //       Disable repaint manager setup for now
      //val repaintManagerKey = RepaintManager::class.java
      //val appContext = AppContext.getAppContext()
      //appContext.put(repaintManagerKey, HeadlessRepaintManager())

      //RepaintManager.currentManager(null).isDoubleBufferingEnabled = false
    }

    private fun setupSystemProperties() {
      // Setting these properties as run arguments isn't enough because they can be overwritten by JVM
      System.setProperty(ENABLE_PROPERTY_NAME, true.toString())
      System.setProperty("java.awt.graphicsenv", PGraphicsEnvironment::class.java.canonicalName)
      System.setProperty("awt.toolkit", PToolkit::class.java.canonicalName)
      System.setProperty("sun.font.fontmanager", PFontManager::class.java.canonicalName)
      System.setProperty("java.awt.headless", false.toString())
      System.setProperty("swing.bufferPerWindow", false.toString())
      System.setProperty("awt.nativeDoubleBuffering", true.toString())  // enable "native" double buffering to disable db in Swing
      System.setProperty("swing.volatileImageBufferEnabled", false.toString())
    }

    private fun setupSingletons() {
      setupGraphicsEnvironment()
      setupToolkit()
      setupFontManager()
      setupRepaintManager()
    }

    private fun setupAgentSystemProperties() {
      // Setting these properties as run arguments isn't enough because they can be overwritten by JVM
      System.setProperty(ENABLE_PROPERTY_NAME, true.toString())
      System.setProperty("swing.bufferPerWindow", false.toString())
      System.setProperty("swing.volatileImageBufferEnabled", false.toString())
    }

    private fun setupAgentSingletons() {
      setupFontManager()
      setupRepaintManager()
    }

    private fun getMainWindows(): List<PWindow> {
      val ideWindows = PWindow.windows.filter { it.windowType == WindowType.IDEA_WINDOW }

      if (ideWindows.isNotEmpty()) {
        return ideWindows
      }

      return PWindow.windows.firstOrNull()?.let(::listOf).orEmpty()
    }

    private fun Component.shiftBounds(shift: AwtPoint): CommonRectangle {
      return bounds
        .run {
          CommonRectangle(
            (x - shift.x).toDouble(),
            (y - shift.y).toDouble(),
            width.toDouble(),
            height.toDouble()
          )
        }
    }

    private fun AwtPoint.shift(shift: AwtPoint): Point {
      return Point(
        (x - shift.x).toDouble(),
        (y - shift.y).toDouble()
      )
    }

    private fun calculateMainWindowShift() {
      getMainWindows().firstOrNull()?.target?.let { window ->
        synchronized(window.treeLock) {
          var x = 0.0
          var y = 0.0

          if (window is Frame) {
            window.insets?.let {
              x += it.left
              y += it.top
            }
          }

          window.bounds?.let {
            x += it.x
            y += it.y
          }

          PGraphicsDevice.clientShift.setLocation(x, y)
        }
      }
    }

    private fun resize(width: Int, height: Int) {
      val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
      if (ge is PGraphicsEnvironment) {
        ge.setSize(width, height)
      }

      getMainWindows().map(PWindow::target).let { mainWindows ->
        SwingUtilities.invokeLater {
          mainWindows.forEach {
            val point = AwtPoint(PGraphicsDevice.clientShift)
            var widthWithInsets = width
            var heightWithInsets = height
            if (it is Frame) {
              it.insets?.let { i ->
                point.x -= i.left
                point.y -= i.top

                // since main windows have no borders on the client now, we should move insets out of client's viewport:
                widthWithInsets += i.left + i.right
                heightWithInsets += i.top + i.bottom
              }
            }

            it.setBounds(point.x, point.y, widthWithInsets, heightWithInsets)
            it.revalidate()
          }
        }
      }
    }

    @JvmStatic
    fun startServer(isAgent: Boolean = false): ProjectorServer {
      ProjectorAwtInitializer.initProjectorAwt()

      if (isAgent) {
        // todo: make it work with dynamic agent
        //setupAgentSystemProperties()
        //setupAgentSingletons()
      }
      else {
        setupSystemProperties()
        setupSingletons()
        IjInjectorAgentInitializer.init()  // todo: support variant for agent too
      }

      ProjectorAwtInitializer.initDefaults()  // this should be done after setting classes because some headless operations can happen here

      SettingsInitializer.addTaskToInitializeIdea()

      val port = System.getProperty(PORT_PROPERTY_NAME)?.toIntOrNull() ?: DEFAULT_PORT

      logger.info { "${ProjectorServer::class.simpleName} is starting on port: $port" }
      if (ENABLE_BIG_COLLECTIONS_CHECKS) {
        logger.info { "Currently collections will log size if it exceeds $BIG_COLLECTIONS_CHECKS_START_SIZE" }
      }

      return ProjectorServer(port, LaterInvokator.defaultLaterInvokator, isAgent).also {
        Do exhaustive when (val hint = setSsl(it.httpWsServer::setWebSocketFactory)) {
          null -> logger.info { "WebSocket SSL is disabled" }

          else -> logger.info { "WebSocket SSL is enabled: $hint" }
        }
        it.start()
      }
    }

    private fun setSsl(setWebSocketFactory: (WebSocketServerFactory) -> Unit): String? {
      val sslPropertiesFilePath = getProperty(SSL_ENV_NAME) ?: return null

      try {
        val properties = Properties().apply {
          load(FileInputStream(sslPropertiesFilePath))
        }

        fun Properties.getOrThrow(key: String) = requireNotNull(this.getProperty(key)) { "Can't find $key in properties file" }

        val storetype = properties.getOrThrow(SSL_STORE_TYPE)
        val filePath = properties.getOrThrow(SSL_FILE_PATH)
        val storePassword = properties.getOrThrow(SSL_STORE_PASSWORD)
        val keyPassword = properties.getOrThrow(SSL_KEY_PASSWORD)

        val keyStore = KeyStore.getInstance(storetype).apply {
          load(FileInputStream(filePath), storePassword.toCharArray())
        }

        val keyManagerFactory = KeyManagerFactory.getInstance("SunX509").apply {
          init(keyStore, keyPassword.toCharArray())
        }

        val trustManagerFactory = TrustManagerFactory.getInstance("SunX509").apply {
          init(keyStore)
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
          init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        }

        setWebSocketFactory(DefaultSSLWebSocketServerFactory(sslContext))

        return sslPropertiesFilePath
      }
      catch (t: Throwable) {
        logger.info(t) { "Can't enable SSL" }

        return null
      }
    }

    const val ENABLE_PROPERTY_NAME = "org.jetbrains.projector.server.enable"
    const val PORT_PROPERTY_NAME = "org.jetbrains.projector.server.port"
    const val DEFAULT_PORT = 8887
    const val TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_HANDSHAKE_TOKEN"
    const val RO_TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_RO_HANDSHAKE_TOKEN"

    const val SSL_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_SSL_PROPERTIES_PATH"
    const val SSL_STORE_TYPE = "STORE_TYPE"
    const val SSL_FILE_PATH = "FILE_PATH"
    const val SSL_STORE_PASSWORD = "STORE_PASSWORD"
    const val SSL_KEY_PASSWORD = "KEY_PASSWORD"

    var ENABLE_BIG_COLLECTIONS_CHECKS = System.getProperty("org.jetbrains.projector.server.debug.collections.checks") == "true"
    private const val DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE = 10_000
    var BIG_COLLECTIONS_CHECKS_START_SIZE =
      System.getProperty("org.jetbrains.projector.server.debug.collections.checks.size")?.toIntOrNull()
      ?: DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE

    const val ENABLE_AUTO_KEYMAP_SETTING = "ORG_JETBRAINS_PROJECTOR_SERVER_AUTO_KEYMAP"
    const val ENABLE_CONNECTION_CONFIRMATION = "ORG_JETBRAINS_PROJECTOR_SERVER_CONNECTION_CONFIRMATION"
  }
}
