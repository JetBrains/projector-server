/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. JetBrains designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact JetBrains, Na Hrebenech II 1718/10, Prague, 14000, Czech Republic
 * if you need additional information or have any questions.
 */
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.server

import org.java_websocket.WebSocket
import org.java_websocket.exceptions.WebsocketNotConnectedException
import org.jetbrains.projector.awt.PClipboard
import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.font.PFontManager
import org.jetbrains.projector.awt.image.PGraphics2D
import org.jetbrains.projector.awt.image.PGraphicsEnvironment
import org.jetbrains.projector.awt.image.PVolatileImage
import org.jetbrains.projector.awt.peer.PComponentPeer
import org.jetbrains.projector.awt.peer.PDesktopPeer
import org.jetbrains.projector.awt.peer.PMouseInfoPeer
import org.jetbrains.projector.common.misc.Do
import org.jetbrains.projector.common.protocol.data.ImageData
import org.jetbrains.projector.common.protocol.data.ImageId
import org.jetbrains.projector.common.protocol.handshake.COMMON_VERSION
import org.jetbrains.projector.common.protocol.handshake.ToClientHandshakeFailureEvent
import org.jetbrains.projector.common.protocol.handshake.ToClientHandshakeSuccessEvent
import org.jetbrains.projector.common.protocol.handshake.commonVersionList
import org.jetbrains.projector.common.protocol.toClient.*
import org.jetbrains.projector.common.protocol.toServer.*
import org.jetbrains.projector.server.core.*
import org.jetbrains.projector.server.core.convert.toAwt.*
import org.jetbrains.projector.server.core.convert.toClient.*
import org.jetbrains.projector.server.core.ij.IdeColors
import org.jetbrains.projector.server.core.ij.IjInjectorAgentInitializer
import org.jetbrains.projector.server.core.ij.KeymapSetter
import org.jetbrains.projector.server.core.ij.SettingsInitializer
import org.jetbrains.projector.server.core.ij.log.DelegatingJvmLogger
import org.jetbrains.projector.server.core.ij.md.PanelUpdater
import org.jetbrains.projector.server.core.protocol.HandshakeTypesSelector
import org.jetbrains.projector.server.core.protocol.KotlinxJsonToClientHandshakeEncoder
import org.jetbrains.projector.server.core.protocol.KotlinxJsonToServerHandshakeDecoder
import org.jetbrains.projector.server.core.util.*
import org.jetbrains.projector.server.idea.CaretInfoUpdater
import org.jetbrains.projector.server.service.ProjectorAwtInitializer
import org.jetbrains.projector.server.service.ProjectorDrawEventQueue
import org.jetbrains.projector.server.service.ProjectorImageCacher
import org.jetbrains.projector.server.util.*
import org.jetbrains.projector.util.logging.Logger
import org.jetbrains.projector.util.logging.loggerFactory
import sun.awt.AWTAccessor
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.peer.ComponentPeer
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.math.roundToLong
import java.awt.Point as AwtPoint

class ProjectorServer private constructor(
  host: InetAddress,
  port: Int,
  private val laterInvokator: LaterInvokator,
  private val isAgent: Boolean,
) {

  private val httpWsServer = object : ProjectorHttpWsServer(host, port) {

    override fun onStart() {
      logger.info { "Server started on host $host and port $port" }

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

          connection.setAttachment(ReadyClientSettings(
            clientSettings.connectionMillis,
            clientSettings.address,
            clientSettings.setUpClientData,
            if (ENABLE_BIG_COLLECTIONS_CHECKS) BIG_COLLECTIONS_CHECKS_START_SIZE else null,
          ))

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
      val clientSettings = connection.getAttachment<ClientSettings>()!!
      val connectionTime = (System.currentTimeMillis() - clientSettings.connectionMillis) / 1000.0
      logger.info { "${clientSettings.address} disconnected, was connected for ${connectionTime.roundToLong()} s." }
    }

    override fun onWsOpen(connection: WebSocket) {
      val address = connection.remoteSocketAddress?.address?.hostAddress
      connection.setAttachment(ConnectedClientSettings(connectionMillis = System.currentTimeMillis(), address = address))
      logger.info { "$address connected." }
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

  val wasStarted: Boolean by httpWsServer::wasStarted

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
      markdownQueue.add(ServerMarkdownEvent.ServerMarkdownMoveEvent(id, point.shift(PGraphicsEnvironment.defaultDevice.clientShift)))
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
    PDesktopPeer.browseUriCallback = { link ->
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
          bounds = window.target.shiftBounds(PGraphicsEnvironment.defaultDevice.clientShift),
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
        val shiftedMessage = message.shift(PGraphicsEnvironment.defaultDevice.clientShift)

        PMouseInfoPeer.lastMouseCoords.setLocation(shiftedMessage.x, shiftedMessage.y)

        val window = PWindow.getWindow(message.windowId)?.target
        PMouseInfoPeer.lastWindowUnderMouse = window

        window ?: return@invokeLater

        val newTouchState = calculateNewTouchState(shiftedMessage, message, clientSettings.touchState) ?: return@invokeLater
        val mouseEvent = createMouseEvent(window, shiftedMessage, clientSettings.touchState, newTouchState, clientSettings.connectionMillis)
        clientSettings.touchState = newTouchState
        laterInvokator(mouseEvent)
      }

      is ClientWheelEvent -> SwingUtilities.invokeLater {
        val shiftedMessage = message.shift(PGraphicsEnvironment.defaultDevice.clientShift)
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
      )
        .let {
          SwingUtilities.invokeLater {
            laterInvokator(it)
          }
        }

      is ClientKeyPressEvent -> message.toAwtKeyEvent(
        connectionMillis = clientSettings.connectionMillis,
        target = focusOwnerOrTarget(PWindow.windows.last().target),
      )
        .let {
          SwingUtilities.invokeLater {
            laterInvokator(it)
          }
        }

      is ClientRawKeyEvent -> SwingUtilities.invokeLater {
          laterInvokator(message.toAwtKeyEvent(
            connectionMillis = clientSettings.connectionMillis,
            target = focusOwnerOrTarget(PWindow.windows.last().target),
          ))
        }

      is ClientRequestImageDataEvent -> {
        val imageData = ProjectorImageCacher.getImage(message.imageId) ?: ImageData.Empty

        val resource = ServerImageDataReplyEvent(message.imageId, imageData)
        clientSettings.requestedData.add(resource)
      }

      is ClientClipboardEvent -> {
        val transferable = object : Transferable {

          override fun getTransferData(flavor: DataFlavor?): Any {
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

      is ClientWindowSetBoundsEvent -> {
        SwingUtilities.invokeLater {
          PWindow.getWindow(message.windowId)?.apply { with(message.bounds) { this@apply.setBounds(x, y, width, height) } }
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

    val remoteAddress = conn.remoteSocketAddress?.address

    if (
      isAgent && remoteAddress?.isLoopbackAddress != true &&
      getProperty(ENABLE_CONNECTION_CONFIRMATION)?.toBoolean() != false
    ) {
      logger.info { "Asking for connection confirmation because of agent mode..." }

      var resp = false

      SwingUtilities.invokeAndWait {
        val accessType = when (hasWriteAccess) {
          true -> "read-write"
          false -> "read-only"
        }

        resp = ConfirmConnection.confirm(remoteAddress, accessType)
      }

      if (!resp) {
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
        address = connectedClientSettings.address,
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
      PGraphicsEnvironment.clientDoesWindowManagement = toServerHandshakeEvent.clientDoesWindowManagement
      PGraphicsEnvironment.setupDisplays(toServerHandshakeEvent.displays.map { Rectangle(it.x, it.y, it.width, it.height) to it.scaleFactor })
      with(toServerHandshakeEvent.displays[0]) { resize(width, height) }
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

  fun getClientList(): Array<Array<String?>> {
    val s = arrayListOf<Array<String?>>()
    httpWsServer.forEachOpenedConnection {
      val remoteAddress = it.remoteSocketAddress?.address
      if (remoteAddress != null) {
        s.add(arrayOf(
          remoteAddress.hostAddress,
          "resolving ..."
        ))
      }
    }
    return s.distinctBy { it[0] }.toTypedArray()
  }

  fun disconnectAll() {
    httpWsServer.forEachOpenedConnection {
      it.close()
    }
  }

  fun disconnectByIp(ip: String) {
    httpWsServer.forEachOpenedConnection {
      if (it.remoteSocketAddress?.address?.hostAddress == ip) {
        it.close()
      }
    }
  }

  companion object {

    private val logger = Logger<ProjectorServer>()

    @JvmStatic
    val isEnabled: Boolean
      get() = System.getProperty(ENABLE_PROPERTY_NAME)?.toBoolean() ?: false

    private fun getMainWindows(): List<PWindow> {
      val ideWindows = PWindow.windows.filter { it.windowType == WindowType.IDEA_WINDOW }

      if (ideWindows.isNotEmpty()) {
        return ideWindows
      }

      return PWindow.windows.firstOrNull()?.let(::listOf).orEmpty()
    }

    private fun calculateMainWindowShift() {
      if (PGraphicsEnvironment.clientDoesWindowManagement) {
        PGraphicsEnvironment.defaultDevice.clientShift.setLocation(0, 0)
        return
      }

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

          PGraphicsEnvironment.defaultDevice.clientShift.setLocation(x, y)
        }
      }
    }

    private fun resize(width: Int, height: Int) {
      val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
      if (ge is PGraphicsEnvironment) {
        ge.setDefaultDeviceSize(width, height)
      }

      calculateMainWindowShift()  // trigger manual update of clientShift because it can be outdated at the moment

      if (PGraphicsEnvironment.clientDoesWindowManagement) return

      getMainWindows().map(PWindow::target).let { mainWindows ->
        SwingUtilities.invokeLater {
          mainWindows.forEach {
            val point = AwtPoint(PGraphicsEnvironment.defaultDevice.clientShift)
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
              it.extendedState = Frame.NORMAL  // if the window is maximized, disable it to allow resizing
            }

            it.setBounds(point.x, point.y, widthWithInsets, heightWithInsets)
            it.revalidate()
          }
        }
      }
    }

    @JvmStatic
    fun startServer(isAgent: Boolean, initializer: Runnable): ProjectorServer {
      loggerFactory = { DelegatingJvmLogger(it) }

      ProjectorAwtInitializer.initProjectorAwt()

      initializer.run()

      IjInjectorAgentInitializer.init(isAgent)

      ProjectorAwtInitializer.initDefaults()  // this should be done after setting classes because some headless operations can happen here

      SettingsInitializer.addTaskToInitializeIdea(PGraphics2D.defaultAa)

      val host = getEnvHost()
      val port = getEnvPort()

      logger.info { "${ProjectorServer::class.simpleName} is starting on host $host and port $port" }
      if (ENABLE_BIG_COLLECTIONS_CHECKS) {
        logger.info { "Currently collections will log size if it exceeds $BIG_COLLECTIONS_CHECKS_START_SIZE" }
      }

      return ProjectorServer(host, port, LaterInvokator.defaultLaterInvokator, isAgent).also {
        val message = when (val hint = setSsl(it.httpWsServer::setWebSocketFactory)) {
          null -> "WebSocket SSL is disabled"
          else -> "WebSocket SSL is enabled: $hint"
        }
        logger.info { message }
        it.start()
      }
    }

    const val ENABLE_PROPERTY_NAME = "org.jetbrains.projector.server.enable"
    const val HOST_PROPERTY_NAME = "org.jetbrains.projector.server.host"
    const val PORT_PROPERTY_NAME = "org.jetbrains.projector.server.port"
    const val DEFAULT_PORT = 8887
    const val TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_HANDSHAKE_TOKEN"
    const val RO_TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_RO_HANDSHAKE_TOKEN"

    var ENABLE_BIG_COLLECTIONS_CHECKS = System.getProperty("org.jetbrains.projector.server.debug.collections.checks") == "true"
    private const val DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE = 10_000
    var BIG_COLLECTIONS_CHECKS_START_SIZE =
      System.getProperty("org.jetbrains.projector.server.debug.collections.checks.size")?.toIntOrNull()
      ?: DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE

    const val ENABLE_AUTO_KEYMAP_SETTING = "ORG_JETBRAINS_PROJECTOR_SERVER_AUTO_KEYMAP"
    const val ENABLE_CONNECTION_CONFIRMATION = "ORG_JETBRAINS_PROJECTOR_SERVER_CONNECTION_CONFIRMATION"

    fun getEnvHost(): InetAddress {
      val host = System.getProperty(HOST_PROPERTY_NAME)
      return if (host != null) InetAddress.getByName(host) else getWildcardHostAddress()
    }

    fun getEnvPort() = System.getProperty(PORT_PROPERTY_NAME)?.toIntOrNull() ?: DEFAULT_PORT
  }
}
