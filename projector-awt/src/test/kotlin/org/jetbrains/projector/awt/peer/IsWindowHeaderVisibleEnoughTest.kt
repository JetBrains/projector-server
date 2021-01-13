/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2021 JetBrains s.r.o.
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
package org.jetbrains.projector.awt.peer

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsWindowHeaderVisibleEnoughTest {

  @Test
  fun `fully visible header should be visible enough`() {
    val headerHeight = 3
    val windowBounds = Rectangle(0, 3, 200, 200)
    val screenBounds = Rectangle(0, 0, 1600, 900)

    assertTrue(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
  }

  @Test
  fun `partial visibility (left)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(-150, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(-50, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }

  @Test
  fun `partial visibility (top)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 5, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 15, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }

  @Test
  fun `partial visibility (right)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(1550, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(1450, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }


  @Test
  fun `partial visibility (bottom)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 915, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 905, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowPeer.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }
}
