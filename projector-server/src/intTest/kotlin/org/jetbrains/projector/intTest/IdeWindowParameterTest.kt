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

import com.codeborne.selenide.CollectionCondition.size
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.open
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.toClient.ServerWindowSetChangedEvent
import org.jetbrains.projector.common.protocol.toClient.WindowData
import org.jetbrains.projector.common.protocol.toClient.WindowType
import org.jetbrains.projector.intTest.ConnectionUtil.clientUrl
import org.jetbrains.projector.intTest.ConnectionUtil.startServerAndDoHandshake
import kotlin.test.Test

class IdeWindowParameterTest {

  private companion object {

    private val windows = listOf(
      WindowData(
        id = 1,
        isShowing = true,
        zOrder = 0,
        bounds = CommonRectangle(10.0, 10.0, 100.0, 100.0),
        resizable = true,
        modal = false,
        undecorated = false,
        windowType = WindowType.IDEA_WINDOW
      ),
      WindowData(
        id = 2,
        isShowing = true,
        zOrder = 0,
        bounds = CommonRectangle(200.0, 20.0, 100.0, 100.0),
        resizable = true,
        modal = false,
        undecorated = false,
        windowType = WindowType.IDEA_WINDOW
      )
    )
  }

  @Test
  fun shouldShowAllWindowsWhenParameterIsNotPresented() {
    val clientLoadNotifier = Channel<Unit>()

    val server = startServerAndDoHandshake { (sender, _) ->
      sender(listOf(ServerWindowSetChangedEvent(windows)))

      clientLoadNotifier.send(Unit)

      for (frame in incoming) {
        // maintaining connection
      }
    }
    server.start()

    open(clientUrl)

    runBlocking {
      clientLoadNotifier.receive()
    }
    elements("canvas.window").shouldHave(size(2))

    server.stop(500, 1000)
  }

  @Test
  fun shouldShowSelectedWindow0WhenParameterIsPresented() {
    val clientLoadNotifier = Channel<Unit>()

    val server = startServerAndDoHandshake { (sender, _) ->
      sender(listOf(ServerWindowSetChangedEvent(windows)))

      clientLoadNotifier.send(Unit)

      for (frame in incoming) {
        // maintaining connection
      }
    }
    server.start()

    open("$clientUrl?ideWindow=0")

    runBlocking {
      clientLoadNotifier.receive()
    }
    elements("canvas.window").shouldHave(size(1))

    server.stop(500, 1000)
  }

  @Test
  fun shouldShowSelectedWindow1WhenParameterIsPresented() {
    val clientLoadNotifier = Channel<Unit>()

    val server = startServerAndDoHandshake { (sender, _) ->
      sender(listOf(ServerWindowSetChangedEvent(windows)))

      clientLoadNotifier.send(Unit)

      for (frame in incoming) {
        // maintaining connection
      }
    }
    server.start()

    open("$clientUrl?ideWindow=1")

    runBlocking {
      clientLoadNotifier.receive()
    }
    elements("canvas.window").shouldHave(size(1))

    server.stop(500, 1000)
  }

  @Test
  fun shouldNotShowNotExistingWindow2WhenParameterIsPresented() {
    val clientLoadNotifier = Channel<Unit>()

    val server = startServerAndDoHandshake { (sender, _) ->
      sender(listOf(ServerWindowSetChangedEvent(windows)))

      clientLoadNotifier.send(Unit)

      for (frame in incoming) {
        // maintaining connection
      }
    }
    server.start()

    open("$clientUrl?ideWindow=2")

    runBlocking {
      clientLoadNotifier.receive()
    }
    elements("canvas.window").shouldHave(size(0))

    server.stop(500, 1000)
  }
}
