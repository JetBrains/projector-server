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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package org.jetbrains.projector.server.util

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.common.protocol.toServer.ClientWindowInterestEvent
import org.mockito.Mockito
import java.awt.Graphics2D
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class InterestManagerImplTest {

  @Test
  fun `test gaining interest triggers PWindow component redraw`() {
    val interestManager = WindowDrawInterestManagerImpl()

    var repaintCounter = 0
    val dummyComponent = object: JPanel() {
      override fun repaint() {
        repaintCounter++
      }
    }
    repaintCounter = 0 // reset it to ignore JPanel's own repaints

    assertEquals(0, PWindow.windows.size, "test assumes there are no windows")

    val window = PWindow.createWithGraphicsOverride(dummyComponent, true, Mockito.mock(Graphics2D::class.java, Mockito.CALLS_REAL_METHODS))
    val windowId = window.id

    try {
      assertEquals(0, repaintCounter)

      interestManager.processClientEvent(ClientWindowInterestEvent(windowId, false))
      assertEquals(0, repaintCounter, "interest loss should not trigger repaint")

      interestManager.processClientEvent(ClientWindowInterestEvent(windowId, true))
      assertEquals(1, repaintCounter, "interest gain should trigger repaint")

      interestManager.processClientEvent(ClientWindowInterestEvent(windowId, true))
      assertEquals(1, repaintCounter, "repeated interest gain should not trigger repaint")

      interestManager.processClientEvent(ClientWindowInterestEvent(windowId, false))
      interestManager.processClientEvent(ClientWindowInterestEvent(windowId, true))
      assertEquals(2, repaintCounter, "interest gain should trigger repaint")
    } finally {
      window.dispose()
      assertEquals(0, PWindow.windows.size, "there should be no windows after test")
    }
  }
}
