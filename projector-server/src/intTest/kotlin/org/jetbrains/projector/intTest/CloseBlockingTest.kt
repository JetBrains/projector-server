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
package org.jetbrains.projector.intTest

import com.codeborne.selenide.Condition.appear
import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Selenide.*
import io.ktor.application.install
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.data.FontDataHolder
import org.jetbrains.projector.common.protocol.data.TtfFontData
import org.jetbrains.projector.common.protocol.handshake.COMMON_VERSION
import org.jetbrains.projector.common.protocol.handshake.ToClientHandshakeSuccessEvent
import org.jetbrains.projector.common.protocol.handshake.commonVersionList
import org.jetbrains.projector.common.protocol.toClient.ServerWindowSetChangedEvent
import org.jetbrains.projector.common.protocol.toClient.ToClientMessageType
import org.jetbrains.projector.common.protocol.toClient.WindowData
import org.jetbrains.projector.common.protocol.toClient.WindowType
import org.jetbrains.projector.common.protocol.toServer.ToServerMessageType
import org.jetbrains.projector.server.protocol.HandshakeTypesSelector
import org.jetbrains.projector.server.protocol.KotlinxJsonToClientHandshakeEncoder
import org.jetbrains.projector.server.protocol.KotlinxJsonToServerHandshakeDecoder
import java.io.File
import java.util.*
import kotlin.test.*

class CloseBlockingTest {

  private companion object {

    private val clientFile = File("../../projector-client/projector-client-web/build/distributions/index.html")
    private val clientUrl = "file://${clientFile.absolutePath}"

    private fun openClientAndActivatePage() {
      open(clientUrl)
      element("body").click(5, 5)  // enable onbeforeunload listener, can't click without arguments because of an exception
    }

    private fun isAlertPresent(): Boolean {
      try {
        switchTo().alert()
        return true
      }
      catch (e: Throwable) {
        if ("NoAlertPresentException" == e::class.java.simpleName) {
          return false
        }

        throw IllegalStateException("Unexpected exception while checking for alert existence", e)
      }
    }

    private fun getFontHolderData(): FontDataHolder {
      val data = File("src/main/resources/fonts/Default-R.ttf").readBytes()
      val base64 = String(Base64.getEncoder().encode(data))

      return FontDataHolder(0, TtfFontData(ttfBase64 = base64))
    }

    private data class SenderReceiver(
      val sender: suspend (ToClientMessageType) -> Unit,
      val receiver: suspend () -> ToServerMessageType
    )

    private suspend fun DefaultWebSocketServerSession.doHandshake(): SenderReceiver {
      val handshakeText = (incoming.receive() as Frame.Text).readText()
      val toServerHandshakeEvent = KotlinxJsonToServerHandshakeDecoder.decode(handshakeText)

      assertEquals(COMMON_VERSION, toServerHandshakeEvent.commonVersion,
                   "Incompatible common protocol versions: server - $COMMON_VERSION (#${commonVersionList.indexOf(COMMON_VERSION)}), " +
                   "client - ${toServerHandshakeEvent.commonVersion} (#${toServerHandshakeEvent.commonVersionId})"
      )

      val toClientCompressor = HandshakeTypesSelector.selectToClientCompressor(toServerHandshakeEvent.supportedToClientCompressions)
      assertNotNull(toClientCompressor,
                    "Server doesn't support any of the following to-client compressions: ${toServerHandshakeEvent.supportedToClientCompressions}"
      )

      val toClientEncoder = HandshakeTypesSelector.selectToClientEncoder(toServerHandshakeEvent.supportedToClientProtocols)
      assertNotNull(toClientEncoder) {
        "Server doesn't support any of the following to-client protocols: ${toServerHandshakeEvent.supportedToClientProtocols}"
      }

      val toServerDecompressor = HandshakeTypesSelector.selectToServerDecompressor(toServerHandshakeEvent.supportedToServerCompressions)
      assertNotNull(toServerDecompressor) {
        "Server doesn't support any of the following to-server compressions: ${toServerHandshakeEvent.supportedToServerCompressions}"
      }

      val toServerDecoder = HandshakeTypesSelector.selectToServerDecoder(toServerHandshakeEvent.supportedToServerProtocols)
      assertNotNull(toServerDecoder) {
        "Server doesn't support any of the following to-server protocols: ${toServerHandshakeEvent.supportedToServerProtocols}"
      }

      val successEvent = ToClientHandshakeSuccessEvent(
        toClientCompression = toClientCompressor.compressionType,
        toClientProtocol = toClientEncoder.protocolType,
        toServerCompression = toServerDecompressor.compressionType,
        toServerProtocol = toServerDecoder.protocolType,
        fontDataHolders = listOf(getFontHolderData()),
        colors = null
      )
      outgoing.send(Frame.Binary(true, KotlinxJsonToClientHandshakeEncoder.encode(successEvent)))

      incoming.receive()  // this message means the client is ready

      return SenderReceiver(
        sender = { outgoing.send(Frame.Binary(true, toClientCompressor.compress(toClientEncoder.encode(it)))) },
        receiver = { toServerDecoder.decode(toServerDecompressor.decompress((incoming.receive() as Frame.Text).readText())) }
      )
    }
  }

  @Test
  fun shouldBeAbleToCloseBefore() {
    openClientAndActivatePage()
    element("body").shouldHave(text("reconnect"))
    refresh()
    assertFalse(isAlertPresent())
  }

  @Test
  fun shouldBeUnableToCloseWhenConnected() {
    val clientLoadNotifier = Channel<Unit>()

    val server = embeddedServer(Netty, 8887) {
      install(WebSockets)

      routing {
        webSocket("/") {
          val (sender, _) = doHandshake()

          val window = WindowData(
            id = 1,
            isShowing = true,
            zOrder = 0,
            bounds = CommonRectangle(10.0, 10.0, 100.0, 100.0),
            resizable = true,
            modal = false,
            undecorated = false,
            windowType = WindowType.IDEA_WINDOW
          )

          sender(listOf(ServerWindowSetChangedEvent(listOf(window))))

          clientLoadNotifier.send(Unit)

          for (frame in incoming) {
            // maintaining connection
          }
        }
      }
    }
    server.start()

    openClientAndActivatePage()

    runBlocking {
      clientLoadNotifier.receive()
    }
    element("canvas.window").should(appear)

    refresh()
    assertTrue(isAlertPresent())
    confirm()

    server.stop(500, 1000)
  }

  @Test
  fun shouldBeAbleToCloseAfterConnectionEnds() {
    val clientLoadNotifier = Channel<Unit>()

    val server = embeddedServer(Netty, 8887) {
      install(WebSockets)

      routing {
        webSocket("/") {
          doHandshake()

          close()

          clientLoadNotifier.send(Unit)
        }
      }
    }
    server.start()

    openClientAndActivatePage()

    runBlocking {
      clientLoadNotifier.receive()
    }
    element("body").shouldHave(text("ended"))

    refresh()
    assertFalse(isAlertPresent())

    server.stop(500, 1000)
  }
}
