/*
 * Copyright (c) 2019-2023, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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
package org.jetbrains.projector.awt

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

    assertTrue(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
  }

  @Test
  fun `partial visibility (left)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(-150, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(-50, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }

  @Test
  fun `partial visibility (top)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 5, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 15, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }

  @Test
  fun `partial visibility (right)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(1550, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(1450, 100, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }


  @Test
  fun `partial visibility (bottom)`() {
    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 915, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertFalse(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }

    run {
      val headerHeight = 20
      val windowBounds = Rectangle(100, 905, 200, 200)
      val screenBounds = Rectangle(0, 0, 1600, 900)

      assertTrue(PWindowUtils.isWindowHeaderVisibleEnough(headerHeight, windowBounds, screenBounds))
    }
  }
}
