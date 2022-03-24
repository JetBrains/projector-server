/*
 * Copyright (c) 2019-2022, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
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
import org.jetbrains.projector.ij.jcef.*
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
import org.jetbrains.projector.server.idea.forbidUpdates
import org.jetbrains.projector.server.service.ProjectorAwtInitializer
import org.jetbrains.projector.server.service.ProjectorDrawEventQueue
import org.jetbrains.projector.server.service.ProjectorImageCacher
import org.jetbrains.projector.server.util.*
import org.jetbrains.projector.server.websocket.WebsocketServer
import org.jetbrains.projector.util.loading.UseProjectorLoader
import org.jetbrains.projector.util.loading.state.IdeState
import org.jetbrains.projector.util.logging.Logger
import org.jetbrains.projector.util.logging.loggerFactory
import sun.awt.AWTAccessor
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.WindowEvent
import java.awt.peer.ComponentPeer
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.lang.Thread.sleep
import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.math.roundToLong
import java.awt.Point as AwtPoint

@UseProjectorLoader
class ProjectorServer private constructor(
  private val laterInvokator: LaterInvokator,
  private val isAgent: Boolean,
) {
  private val transports: MutableSet<ServerTransport> = ConcurrentHashMap<ServerTransport, Unit>().keySet(Unit)

  // Reading the wasStarted property will block execution of the thread until
  // at least one transport will be successfully initialized
  // or all transports will be initialized unsuccessfully
  val wasStarted: Boolean
    get() {
      return transports.any { it.wasStarted }
    }

  private lateinit var updateThread: Thread

  private val commonQueue = ConcurrentLinkedQueue<ServerEvent>()

  private val caretInfoUpdater = CaretInfoUpdater { caretInfo ->
    commonQueue.add(ServerCaretInfoChangedEvent(caretInfo))
  }

  private var windowColorsEvent: ServerWindowColorsEvent? = null

  private val ideaColors = IdeColors { colors ->
    windowColorsEvent = ServerWindowColorsEvent(colors)
  }

  init {
    PanelUpdater.showCallback = { id, show ->
      commonQueue.add(ServerMarkdownEvent.ServerMarkdownShowEvent(id, show))
    }
    PanelUpdater.resizeCallback = { id, size ->
      commonQueue.add(ServerMarkdownEvent.ServerMarkdownResizeEvent(id, size.toCommonIntSize()))
    }
    PanelUpdater.moveCallback = { id, point ->
      commonQueue.add(ServerMarkdownEvent.ServerMarkdownMoveEvent(id, point.shift(PGraphicsEnvironment.defaultDevice.clientShift)))
    }
    PanelUpdater.disposeCallback = { id ->
      commonQueue.add(ServerMarkdownEvent.ServerMarkdownDisposeEvent(id))
    }
    PanelUpdater.placeToWindowCallback = { id, rootComponent ->
      rootComponent?.let {
        val peer = AWTAccessor.getComponentAccessor().getPeer<ComponentPeer>(it)

        if (peer !is PComponentPeer) {
          return@let
        }

        commonQueue.add(ServerMarkdownEvent.ServerMarkdownPlaceToWindowEvent(id, peer.pWindow.id))
      }
    }
    PanelUpdater.setHtmlCallback = { id, html ->
      commonQueue.add(ServerMarkdownEvent.ServerMarkdownSetHtmlEvent(id, html))
    }
    PanelUpdater.setCssCallback = { id, css ->
      commonQueue.add(ServerMarkdownEvent.ServerMarkdownSetCssEvent(id, css))
    }
    PanelUpdater.scrollCallback = { id, offset ->
      commonQueue.add(ServerMarkdownEvent.ServerMarkdownScrollEvent(id, offset))
    }
    PDesktopPeer.browseUriCallback = { link ->
      commonQueue.add(ServerBrowseUriEvent(link))
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")  // used in CWM
  val clientEventHandler: ClientEventHandler = object : ClientEventHandler {
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
          PWindow.windows.forEach(PWindow::repaint)
          previousWindowEvents = emptySet()
          caretInfoUpdater.createCaretInfoEvent()
          PanelUpdater.updateAll()
          updateCefBrowsersSafely()
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
      notifyObservers(clientsCountMessage(this, count))
    }
  }

  private val observers = Collections.synchronizedList(mutableListOf<PropertyChangeListener>())
  fun addObserver(listener: PropertyChangeListener) = observers.add(listener)
  fun removeObserver(listener: PropertyChangeListener) = observers.remove(listener)

  private fun notifyObservers(event: PropertyChangeEvent) = observers.forEach { it.propertyChange(event) }

  private fun sendMacLocalConnectionWarning(address: InetAddress) {
    notifyObservers(macLocalConnectionMessage(this, address))
  }

  private fun createUpdateThread(): Thread = thread(isDaemon = true) {
    // TODO: remove this thread: encapsulate the logic in an extracted class and maybe even don't use threads but coroutines' channels
    logger.debug { "Daemon thread starts" }
    while (!Thread.currentThread().isInterrupted) {
      try {
        val dataToSend = createDataToSend()  // creating data even if there are no clients to avoid memory leaks
        sendPictures(dataToSend)

        dataToSend
          .distinctUpdatedOnscreenSurfaces()
          .map { it to listOf(Flush) }
          // don't call SwingUtilities.invokeLater when unneeded. also, we shouldn't touch EDT too early because it's overridden by IJ and
          // this results in multiple EDT living at the same time, creating nasty exceptions like "no ComponentUI class for":
          .takeIf { it.isNotEmpty() }
          ?.let {
            SwingUtilities.invokeLater {
              // create FLUSH commands: we can flush for sure when no other painting is in progress,
              // and seems like it's when all operations in EDT are finished and a new one is started
              ProjectorDrawEventQueue.commands.addAll(it)
            }
          }

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
  private fun createDataToSend(): List<ServerEvent> {
    val clipboardEvent = when (isAgent) {
      false -> PClipboard.extractLastContents()?.toServerClipboardEvent().let(::listOfNotNull)
      true -> {
        val systemClipboard = Toolkit.getDefaultToolkit().systemClipboard
        val clipboardEvent = try {
          systemClipboard.getContents(null)
        }
        catch (e: IllegalStateException) {
          null  // https://youtrack.jetbrains.com/issue/PRJ-744
        }?.toServerClipboardEvent()

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

    val drawCommands = extractData(ProjectorDrawEventQueue.commands).shrinkEvents()

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
          windowType = window.windowType,
          windowClass = window.windowClass,
          isAutoRequestFocus = window.isAutoRequestFocus,
          isAlwaysOnTop = window.isAlwaysOnTop,
          parentId = window.parentWindow?.id,
          renderingScale = window.renderingScale
        )
      }

    val windowSetChangedEvent = when {
      areChangedWindows(windows) -> listOf(ServerWindowSetChangedEvent(windows))
      else -> emptyList()
    }

    val newImagesCopy = extractData(ProjectorImageCacher.newImages)

    val commonEvents = extractData(commonQueue)

    val commandsCount = commonEvents.size + newImagesCopy.size + clipboardEvent.size + drawCommands.size + windowSetChangedEvent.size + 1

    val allEvents = buildList(commandsCount) {
      addAll(commonEvents)
      addAll(newImagesCopy)
      addAll(clipboardEvent)
      addAll(drawCommands)
      addAll(windowSetChangedEvent)
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
        if (!window.isShowing) return@invokeLater
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
          getOption(ENABLE_AUTO_KEYMAP_SETTING, "true").toBoolean() -> KeymapSetter.setKeymap(message.keymap)
          else -> logger.info { "Client keymap was ignored (property specified)!" }
        }
        Do exhaustive when {
          isAgent -> logger.info { "Don't support matching keyboard modifiers mode in agent mode yet" }
          getOption(MAC_KEYBOARD_MODIFIERS_MODE) != null -> {
            val mode = getOption(MAC_KEYBOARD_MODIFIERS_MODE)!!.toBoolean()
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

      is ClientWindowsActivationEvent -> {
        updateWindowsState(message.windowIds, WindowEvent.WINDOW_ACTIVATED)
      }

      is ClientWindowsDeactivationEvent -> {
        updateWindowsState(message.windowIds, WindowEvent.WINDOW_DEACTIVATED)
      }

      is ClientNotificationEvent -> {
        if (!IdeState.isIdeAttached) return

        val intellijNotificationType = when (message.notificationType) {
          ClientNotificationType.INFORMATION -> NotificationType.INFORMATION
          ClientNotificationType.WARNING -> NotificationType.WARNING
          ClientNotificationType.ERROR -> NotificationType.ERROR
        }

        @Suppress("UnresolvedPluginConfigReference")
        val notification = Notification("ProjectorClient", message.title, message.message, intellijNotificationType)
        Notifications.Bus.notify(notification)
      }

      is ClientJcefEvent -> {
        val projectorCefBrowser = ProjectorCefBrowser.getInstance(message.browserId) ?: return

        val messageRouters = projectorCefBrowser.client.getMessageRouters()
        val eventRouter = messageRouters.find {
          it.messageRouterConfig?.jsQueryFunction == message.functionName
        } ?: return

        val handlers = eventRouter.getHandlers()
        handlers.forEach {
          it.onProjectorQuery(projectorCefBrowser, message.data)
        }
      }
    }
  }

  private fun updateWindowsState(windowIds: List<Int>, windowEventId: Int) {
    with(Toolkit.getDefaultToolkit().systemEventQueue) {
      for (windowId in windowIds) {
        val window = PWindow.getWindow(windowId)?.target as? Window ?: continue
        postEvent(WindowEvent(window, windowEventId))
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
      getOption(TOKEN_ENV_NAME) -> true
      getOption(RO_TOKEN_ENV_NAME) -> false
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
      getOption(ENABLE_CONNECTION_CONFIRMATION)?.toBoolean() != false
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

    if (isAgent) {
      val remoteIp = conn.confirmationRemoteIp

      if (isMac && remoteIp != null && isLocalAddress(remoteIp)) {
        sendMacLocalConnectionWarning(remoteIp)
      }
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
      SwingUtilities.invokeAndWait {
        PGraphicsEnvironment.setupDisplays(
          toServerHandshakeEvent.displays.map { Rectangle(it.x, it.y, it.width, it.height) to it.scaleFactor })
        with(toServerHandshakeEvent.displays[0]) { resize(width, height) }
      }
    }

    clientEventHandler.updateClientsCount()
  }

  private fun sendPictures(dataToSend: List<ServerEvent>) {
    transports.forEach { transport ->
      transport.forEachOpenedConnection { client ->
        val readyClientSettings = client.settings as? ReadyClientSettings ?: return@forEachOpenedConnection

        val compressed = with(readyClientSettings.setUpClientData) {
          val requestedData = extractData(readyClientSettings.requestedData)
          val message = readyClientSettings.interestManager.filterEvents(requestedData.asSequence() + dataToSend.asSequence()).toList()

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

    WebsocketServer.createTransportBuilders().forEach {
      addTransport(it.attachDefaultServerEventHandlers(clientEventHandler).build())
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

      if (!isAgent && getOption(DISABLE_IDEA_UPDATES_PROPERTY_NAME, "true").toBoolean()) {
        forbidUpdates()
      }

      if (ENABLE_BIG_COLLECTIONS_CHECKS) {
        logger.info { "Currently collections will log size if it exceeds $BIG_COLLECTIONS_CHECKS_START_SIZE" }
      }

      return ProjectorServer(LaterInvokator.defaultLaterInvokator, isAgent).also {
        lastStartedServer = it
        it.start()
      }
    }

    fun appendToCommonQueue(event: ServerEvent) {
      lastStartedServer?.apply { commonQueue += event }
    }

    @Suppress("MemberVisibilityCanBePrivate")  // used in CWM
    var lastStartedServer: ProjectorServer? = null
      private set

    const val ENABLE_PROPERTY_NAME = "org.jetbrains.projector.server.enable"
    private const val HOST_PROPERTY_NAME_OLD = "org.jetbrains.projector.server.host"
    const val HOST_PROPERTY_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_HOST"
    private const val PORT_PROPERTY_NAME_OLD = "org.jetbrains.projector.server.port"
    const val PORT_PROPERTY_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_PORT"
    private const val DEFAULT_PORT = "8887"
    const val TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_HANDSHAKE_TOKEN"
    const val RO_TOKEN_ENV_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_RO_HANDSHAKE_TOKEN"

    var ENABLE_BIG_COLLECTIONS_CHECKS = System.getProperty("org.jetbrains.projector.server.debug.collections.checks") == "true"
    private const val DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE = 10_000
    var BIG_COLLECTIONS_CHECKS_START_SIZE =
      System.getProperty("org.jetbrains.projector.server.debug.collections.checks.size")?.toIntOrNull()
      ?: DEFAULT_BIG_COLLECTIONS_CHECKS_SIZE

    const val ENABLE_AUTO_KEYMAP_SETTING = "ORG_JETBRAINS_PROJECTOR_SERVER_AUTO_KEYMAP"
    const val MAC_KEYBOARD_MODIFIERS_MODE = "ORG_JETBRAINS_PROJECTOR_SERVER_MAC_KEYBOARD"
    const val ENABLE_CONNECTION_CONFIRMATION = "ORG_JETBRAINS_PROJECTOR_SERVER_CONNECTION_CONFIRMATION"
    private const val DISABLE_IDEA_UPDATES_PROPERTY_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_DISABLE_IDEA_UPDATES"

    internal fun getEnvHost(): InetAddress {
      val host = getOption(HOST_PROPERTY_NAME) ?: getOption(HOST_PROPERTY_NAME_OLD)
      return if (host != null) InetAddress.getByName(host) else getWildcardHostAddress()
    }

    fun getEnvPort() = (getOption(PORT_PROPERTY_NAME) ?: getOption(PORT_PROPERTY_NAME_OLD, DEFAULT_PORT)).toInt()

    private val LOCAL_ADDRESSES get() = getLocalAddresses(keepIpv6 = true).map { it.address }

    fun isLocalAddress(address: InetAddress) = address in LOCAL_ADDRESSES

    private val isMac get() = System.getProperty("os.name").startsWith("Mac OS")
  }
}
