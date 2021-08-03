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

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.EditorFactory
import org.jetbrains.projector.awt.PClipboard
import org.jetbrains.projector.awt.PToolkit
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
import org.jetbrains.projector.common.protocol.data.UserKeymap
import org.jetbrains.projector.common.protocol.handshake.*
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
import org.jetbrains.projector.server.core.util.LaterInvokator
import org.jetbrains.projector.server.core.util.focusOwnerOrTarget
import org.jetbrains.projector.server.core.util.getProperty
import org.jetbrains.projector.server.core.util.getWildcardHostAddress
import org.jetbrains.projector.server.idea.CaretInfoUpdater
import org.jetbrains.projector.server.service.ProjectorAwtInitializer
import org.jetbrains.projector.server.service.ProjectorDrawEventQueue
import org.jetbrains.projector.server.service.ProjectorImageCacher
import org.jetbrains.projector.server.util.*
import org.jetbrains.projector.server.websocket.WebsocketServer
import org.jetbrains.projector.util.logging.Logger
import org.jetbrains.projector.util.logging.loggerFactory
import sun.awt.AWTAccessor
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.peer.ComponentPeer
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.lang.Thread.sleep
import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.roundToLong
import kotlin.properties.Delegates
import java.awt.Point as AwtPoint

class ProjectorServer private constructor(
  private val laterInvokator: LaterInvokator,
  private val isAgent: Boolean,
) {
  private val transports: MutableSet<ServerTransport> = ConcurrentHashMap<ServerTransport, Unit>().keySet(Unit)

  val wasStarted: Boolean
    get() {
      return transports.all { it.wasStarted }
    }

  private lateinit var updateThread: Thread

  private val caretInfoQueue = ConcurrentLinkedQueue<ServerCaretInfoChangedEvent.CaretInfoChange>()

  private val caretInfoUpdater = CaretInfoUpdater { caretInfo ->
    caretInfoQueue.add(caretInfo)
  }

  private val markdownQueue = ConcurrentLinkedQueue<ServerMarkdownEvent>()

  private val speculativeQueue = ConcurrentLinkedQueue<Pair<SpeculativeEvent, String>>()

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

  @Suppress("MemberVisibilityCanBePrivate")  // used in CWM
  val clientEventHandler : ClientEventHandler = object : ClientEventHandler {
    override fun onClientConnectionEnded(connection: ClientWrapper) {
      val clientSettings = connection.settings
      val connectionTime = (System.currentTimeMillis() - clientSettings.connectionMillis) / 1000.0
      logger.info { "${clientSettings.address} disconnected, was connected for ${connectionTime.roundToLong()} s." }
    }

    override fun getInitialClientState(address: String?): ClientSettings {
      return ConnectedClientSettings(connectionMillis = System.currentTimeMillis(), address = address)
    }

    override fun onClientConnected(connection: ClientWrapper) {
      // do nothing for now
    }

    override fun handleMessage(wrapper: ClientWrapper, message: String) {
      Do exhaustive when (val clientSettings = wrapper.settings) {
        is ConnectedClientSettings -> checkHandshakeVersion(wrapper, clientSettings, message)

        is SupportedHandshakeClientSettings -> setUpClient(wrapper, clientSettings, message)

        is SetUpClientSettings -> {
          // this means that the client has loaded fonts and is ready to draw
          wrapper.settings = ReadyClientSettings(
            clientSettings.connectionMillis,
            clientSettings.address,
            clientSettings.setUpClientData,
            WindowDrawInterestManagerImpl(),
            if (ENABLE_BIG_COLLECTIONS_CHECKS) BIG_COLLECTIONS_CHECKS_START_SIZE else null,
          )

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

        is ClosedClientSettings -> {
          // ignoring closed client
        }
      }
    }

    override fun updateClientsCount() {
      val count = transports.sumOf { it.clientCount }

      clientsCountLock.withLock { clientsCount = count }
    }
  }

  private val clientsObservers = Collections.synchronizedList(mutableListOf<PropertyChangeListener>())
  fun addClientsObserver(listener: PropertyChangeListener) = clientsObservers.add(listener)
  fun removeClientsObserver(listener: PropertyChangeListener) = clientsObservers.remove(listener)

  private val clientsCountLock = ReentrantLock()
  private var clientsCount: Int by Delegates.observable(0) { _, _, newValue ->
    clientsObservers.forEach { listener ->
      listener.propertyChange(PropertyChangeEvent(this, "clientsCount", null, newValue))
    }
  }

  private fun createUpdateThread(): Thread = thread(isDaemon = true) {
    // TODO: remove this thread: encapsulate the logic in an extracted class and maybe even don't use threads but coroutines' channels
    logger.debug { "Daemon thread starts" }
    while (!Thread.currentThread().isInterrupted) {
      try {
        val dataToSend = createDataToSend()  // creating data even if there are no clients to avoid memory leaks
        sendPictures(dataToSend)

        sleep(10)
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

  private var lastClipboardEvent: ServerClipboardEvent? = null

  private fun isClipboardChanged(current: ServerClipboardEvent?) = lastClipboardEvent != current

  @OptIn(ExperimentalStdlibApi::class)
  private fun createDataToSend(): List<FilterableEvent<*>> {
    val clipboardEvent = when (isAgent) {
      false -> PClipboard.extractLastContents()?.toServerClipboardEvent().let(::listOfNotNull)
      true -> {
        val clipboardEvent = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)?.toServerClipboardEvent()

        if (isClipboardChanged(clipboardEvent)) {
          lastClipboardEvent = clipboardEvent
          clipboardEvent.let(::listOfNotNull)
        }
        else {
          emptyList()
        }
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

    val speculativeEvents = extractData(speculativeQueue).map {
      FilterableEvent(it.first) { _, settings -> it.second == settings.address }
    }

    val commandsCount = caretInfoEvents.size + newImagesCopy.size + clipboardEvent.size + drawCommands.size +
                        windowSetChangedEvent.size + markdownEvents.size + speculativeEvents.size + 1

    fun toFilterableEvent(event: ServerEvent): FilterableEvent<*> {
      return FilterableEvent(event) { _, _ -> true }
    }

    val allEvents = buildList(commandsCount) {
      addAll(caretInfoEvents.map(::toFilterableEvent))
      addAll(newImagesCopy.map(::toFilterableEvent))
      addAll(clipboardEvent.map(::toFilterableEvent))
      addAll(drawCommands.map(::toFilterableEvent))
      addAll(windowSetChangedEvent.map(::toFilterableEvent))
      addAll(markdownEvents.map(::toFilterableEvent))
      addAll(speculativeEvents)
      windowColorsEvent?.let { add(toFilterableEvent(it)); windowColorsEvent = null }
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

      is ClientDisplaySetChangeEvent -> SwingUtilities.invokeLater {
        PGraphicsEnvironment.setupDisplays(message.newDisplays.map { Rectangle(it.x, it.y, it.width, it.height) to it.scaleFactor })
      }

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

        // shouldn't be called in EDT since setContents calls invokeLater itself and adds extra delay
        when (isAgent) {
          true -> Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
          false -> PClipboard.putContentsWithoutLastContentsUpdate(transferable)
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

      is ClientSetKeymapEvent -> {
        Do exhaustive when {
          isAgent -> logger.info { "Client keymap was ignored (agent mode)!" }
          getProperty(ENABLE_AUTO_KEYMAP_SETTING)?.toBoolean() == false -> logger.info { "Client keymap was ignored (property specified)!" }
          else -> KeymapSetter.setKeymap(message.keymap)
        }
        Do exhaustive when {
          isAgent -> logger.info { "Don't support matching keyboard modifiers mode in agent mode yet" }
          getProperty(MAC_KEYBOARD_MODIFIERS_MODE) != null -> {
            val mode = getProperty(MAC_KEYBOARD_MODIFIERS_MODE)!!.toBoolean()
            logger.info { "Force keyboard modifiers to $mode (property specified)" }
            Do exhaustive when (mode) {
              true -> updateToolkitKeyboardModifiersMode(UserKeymap.MAC)
              false -> updateToolkitKeyboardModifiersMode(UserKeymap.LINUX)
            }
          }
          else -> updateToolkitKeyboardModifiersMode(
            message.keymap)  // todo: it doesn't support multiple connected clients: for now need to reconnect to apply settings
        }
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

      is ClientWindowInterestEvent -> SwingUtilities.invokeLater { clientSettings.interestManager.processClientEvent(message) }

      is ClientSpeculativeKeyPressEvent -> {

        val editor = EditorFactory.getInstance().allEditors.find {
          System.identityHashCode(it) == message.editorId
        }

        if (editor == null) {
          processMessage(clientSettings, message.originalEvent) // fallback
        } else {

          invokeAndWaitIfNeeded {
            runWriteAction {
              executeCommand {
                val insertedString = message.originalEvent.char.toString()

                val selectionInfo = message.selectionInfo

                editor.document.apply {
                  if (selectionInfo != null) {
                    replaceString(selectionInfo.startOffset, selectionInfo.endOffset, insertedString)
                  }
                  else {
                    insertString(message.offset, insertedString)
                  }
                }

                val newOffset = (selectionInfo?.startOffset ?: message.offset) + insertedString.length

                editor.caretModel.primaryCaret.apply {
                  removeSelection()
                  moveToOffset(newOffset)
                }
              }
            }
          }
        }

        speculativeQueue.add(SpeculativeEvent.SpeculativeStringDrawnEvent(message.requestId) to clientSettings.address!!)
      }
    }
  }

  private fun checkHandshakeVersion(conn: ClientWrapper, connectedClientSettings: ConnectedClientSettings, message: String) {
    val (handshakeVersion, handshakeVersionId) = message.split(";")
    if (handshakeVersion != "$HANDSHAKE_VERSION") {
      val reason =
        "Incompatible handshake versions: server - $HANDSHAKE_VERSION (#${handshakeVersionList.indexOf(HANDSHAKE_VERSION)}), " +
        "client - $handshakeVersion (#$handshakeVersionId)"
      conn.disconnect(reason)

      return
    }

    conn.settings = SupportedHandshakeClientSettings(
      connectionMillis = connectedClientSettings.connectionMillis,
      address = connectedClientSettings.address,
    )
  }

  private fun setUpClient(conn: ClientWrapper, supportedHandshakeClientSettings: SupportedHandshakeClientSettings, message: String) {
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

    if (
      isAgent && conn.requiresConfirmation &&
      getProperty(ENABLE_CONNECTION_CONFIRMATION)?.toBoolean() != false
    ) {
      logger.info { "Asking for connection confirmation because of agent mode..." }

      var resp = false

      SwingUtilities.invokeAndWait {
        val accessType = when (hasWriteAccess) {
          true -> "read-write"
          false -> "read-only"
        }

        resp = conn.confirmationRemoteIp?.let { ConfirmConnection.confirm(it, accessType) } ?: ConfirmConnection.confirm(
          conn.confirmationRemoteName, accessType)
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

    conn.settings = SetUpClientSettings(
      connectionMillis = supportedHandshakeClientSettings.connectionMillis,
      address = supportedHandshakeClientSettings.address,
      setUpClientData = SetUpClientData(
        hasWriteAccess = hasWriteAccess,
        toClientMessageEncoder = toClientEncoder,
        toClientMessageCompressor = toClientCompressor,
        toServerMessageDecoder = toServerDecoder,
        toServerMessageDecompressor = toServerDecompressor
      )
    )

    if (hasWriteAccess) {
      PGraphicsEnvironment.clientDoesWindowManagement = toServerHandshakeEvent.clientDoesWindowManagement
      PGraphicsEnvironment.setupDisplays(
        toServerHandshakeEvent.displays.map { Rectangle(it.x, it.y, it.width, it.height) to it.scaleFactor })
      with(toServerHandshakeEvent.displays[0]) { resize(width, height) }
    }

    clientEventHandler.updateClientsCount()
  }

  private fun sendPictures(dataToSend: List<FilterableEvent<*>>) {
    transports.forEach { transport ->
      transport.forEachOpenedConnection { client ->
        val readyClientSettings = client.settings as? ReadyClientSettings ?: return@forEachOpenedConnection

        val compressed = with(readyClientSettings.setUpClientData) {
          val requestedData = extractData(readyClientSettings.requestedData)
          val message = readyClientSettings.interestManager.filterEvents(requestedData.asSequence() + dataToSend.mapNotNull {
            when (it.isValidForClient(readyClientSettings)) {
              true -> it.originalEvent
              false -> null
            }
          }.asSequence()).toList()

          if (message.isEmpty()) {
            return@forEachOpenedConnection
          }

          val encoded = toClientMessageEncoder.encode(message)
          toClientMessageCompressor.compress(encoded)
        }

        client.send(compressed)
      }
    }
  }

  private var previousWindowEvents: Set<WindowData> = emptySet()

  private fun areChangedWindows(windowEvents: List<WindowData>): Boolean {
    val set = windowEvents.toSet()
    val hasDifferentWindowEvents = set != previousWindowEvents

    if (hasDifferentWindowEvents) {
      previousWindowEvents = set
    }

    return hasDifferentWindowEvents
  }

  fun start() {
    updateThread = createUpdateThread()
    caretInfoUpdater.start()

    if (getProperty(ENABLE_WS_TRANSPORT_PROPERTY)?.toBoolean() != false) {
      WebsocketServer.createTransportBuilders().forEach {
        addTransport(it.attachDefaultServerEventHandlers(clientEventHandler).build())
      }
    }
  }


  /**
   * Adds the specified transport for this server and starts it
   */
  @Suppress("MemberVisibilityCanBePrivate") // used in CWM
  fun addTransport(transport: ServerTransport) {
    transports.add(transport)
    transport.start()
  }

  /**
   * Removes the specified transports from the server.
   * If this transport was previously added, stops it with given timeout.
   * Returns true if transport was present (and removed), false otherwise.
   */
  @Suppress("MemberVisibilityCanBePrivate", "unused") // used in CWM
  fun removeTransport(transport: ServerTransport, timeoutMs: Int = 0): Boolean {
    val removed = transports.remove(transport)
    if (removed)
      transport.stop(timeoutMs)
    return removed
  }

  @JvmOverloads
  fun stop(timeout: Int = 0) {
    transports.forEach { it.stop(timeout) }
    transports.clear()
    caretInfoUpdater.stop()

    if (::updateThread.isInitialized) {
      updateThread.interrupt()
    }
  }

  fun isStopped() = !::updateThread.isInitialized || updateThread.state == Thread.State.TERMINATED

  fun getClientList(): Array<Array<String?>> {
    val s = arrayListOf<Array<String?>>()
    transports.forEach { transport ->
      transport.forEachOpenedConnection {
        val remoteAddress = it.confirmationRemoteIp
        if (remoteAddress != null) {
          s.add(arrayOf(
            remoteAddress.hostAddress,
            "resolving ..."
          ))
        }
        else {
          val name = it.confirmationRemoteName
          s.add(arrayOf(name, name))
        }
      }
    }
    return s.distinctBy { it[0] }.toTypedArray()
  }

  fun disconnectAll() {
    transports.forEach { transport ->
      transport.forEachOpenedConnection {
        it.disconnect("The host has disconnected all the clients.")
      }
    }
  }

  fun disconnectByIp(ip: String) {
    transports.forEach { transport ->
      transport.forEachOpenedConnection {
        if (it.confirmationRemoteIp?.hostAddress == ip) {
          it.disconnect("The host has disconnected the address: $ip.")
        }
      }
    }
  }

  companion object {

    private val logger = Logger<ProjectorServer>()

    @JvmStatic
    val isEnabled: Boolean
      get() = System.getProperty(ENABLE_PROPERTY_NAME)?.toBoolean() ?: false

    private fun Transferable.toServerClipboardEvent(): ServerClipboardEvent? = when (this.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      false -> null
      true -> ServerClipboardEvent(this.getTransferData(DataFlavor.stringFlavor) as String)
    }

    private fun updateToolkitKeyboardModifiersMode(keymap: UserKeymap) {
      PToolkit.macKeyboardModifiersMode = when (keymap) {
        UserKeymap.WINDOWS, UserKeymap.LINUX -> false
        UserKeymap.MAC -> true
      }
    }

    internal fun getMainWindows(): List<PWindow> {
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

      if (ENABLE_BIG_COLLECTIONS_CHECKS) {
        logger.info { "Currently collections will log size if it exceeds $BIG_COLLECTIONS_CHECKS_START_SIZE" }
      }

      return ProjectorServer(LaterInvokator.defaultLaterInvokator, isAgent).also {
        lastStartedServer = it
        it.start()
      }
    }

    @Suppress("MemberVisibilityCanBePrivate")  // used in CWM
    var lastStartedServer: ProjectorServer? = null
      private set

    const val ENABLE_PROPERTY_NAME = "org.jetbrains.projector.server.enable"
    const val HOST_PROPERTY_NAME_OLD = "org.jetbrains.projector.server.host"
    const val HOST_PROPERTY_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_HOST"
    const val PORT_PROPERTY_NAME_OLD = "org.jetbrains.projector.server.port"
    const val PORT_PROPERTY_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_PORT"
    private const val DEFAULT_PORT = 8887
    const val TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_HANDSHAKE_TOKEN"
    const val RO_TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_RO_HANDSHAKE_TOKEN"
    const val ENABLE_WS_TRANSPORT_PROPERTY = "ORG_JETBRAINS_PROJECTOR_SERVER_ENABLE_WS_TRANSPORT"

    var ENABLE_BIG_COLLECTIONS_CHECKS = System.getProperty("org.jetbrains.projector.server.debug.collections.checks") == "true"
    private const val DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE = 10_000
    var BIG_COLLECTIONS_CHECKS_START_SIZE =
      System.getProperty("org.jetbrains.projector.server.debug.collections.checks.size")?.toIntOrNull()
      ?: DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE

    const val ENABLE_AUTO_KEYMAP_SETTING = "ORG_JETBRAINS_PROJECTOR_SERVER_AUTO_KEYMAP"
    const val MAC_KEYBOARD_MODIFIERS_MODE = "ORG_JETBRAINS_PROJECTOR_SERVER_MAC_KEYBOARD"
    const val ENABLE_CONNECTION_CONFIRMATION = "ORG_JETBRAINS_PROJECTOR_SERVER_CONNECTION_CONFIRMATION"

    internal fun getEnvHost(): InetAddress {
      val host = getProperty(HOST_PROPERTY_NAME) ?: getProperty(HOST_PROPERTY_NAME_OLD)
      return if (host != null) InetAddress.getByName(host) else getWildcardHostAddress()
    }

    fun getEnvPort() = (getProperty(PORT_PROPERTY_NAME) ?: getProperty(PORT_PROPERTY_NAME_OLD))?.toIntOrNull() ?: DEFAULT_PORT
  }

  class FilterableEvent<T: ServerEvent>(val originalEvent: T, private val filter: (T, ReadyClientSettings) -> Boolean) {
    fun isValidForClient(clientSettings: ReadyClientSettings) = filter(originalEvent, clientSettings)
  }
}
