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
import io.ktor.http.cio.websocket.close
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.toClient.ServerWindowSetChangedEvent
import org.jetbrains.projector.common.protocol.toClient.WindowData
import org.jetbrains.projector.common.protocol.toClient.WindowType
import org.jetbrains.projector.intTest.ConnectionUtil.clientUrl
import org.jetbrains.projector.intTest.ConnectionUtil.startServerAndDoHandshake
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloseBlockingTest {

  private companion object {

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

    val server = startServerAndDoHandshake { (sender, _) ->
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
    val connectionEndedNotifier = Channel<Unit>()

    val server = startServerAndDoHandshake {
      close()
      connectionEndedNotifier.send(Unit)
    }
    server.start()

    openClientAndActivatePage()

    runBlocking {
      connectionEndedNotifier.receive()
    }
    element("body").shouldHave(text("ended"))

    refresh()
    assertFalse(isAlertPresent())

    server.stop(500, 1000)
  }
}
