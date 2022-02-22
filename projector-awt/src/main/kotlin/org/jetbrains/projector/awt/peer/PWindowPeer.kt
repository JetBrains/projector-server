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

package org.jetbrains.projector.awt.peer

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.image.PGraphicsEnvironment
import org.jetbrains.projector.util.logging.Logger
import java.awt.Dialog
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.peer.WindowPeer
import kotlin.math.roundToInt

open class PWindowPeer(target: Window) : PContainerPeer(target), WindowPeer {

  override fun toFront() {
    pWindow.toFront()
  }

  override fun toBack() {
    pWindow.toBack()
  }

  private fun transferFocusToOwnerWindow() {
    var targetOwner: Window? = (pWindow.target as Window).owner
    while (targetOwner != null && targetOwner.owner != null && !targetOwner.isFocusableWindow) {
      targetOwner = targetOwner.owner
    }

    // Fallback: try getting last window that was brought to front
    if (targetOwner == null) {
      targetOwner = PWindow.windows.lastOrNull { it !== pWindow && it.target is Window && it.target.isFocusableWindow }?.target as Window?
    }

    if (targetOwner != null) {
      PWindow.getWindow(targetOwner)?.transferNativeFocus()
    }
  }

  override fun dispose() {
    transferFocusToOwnerWindow()

    super.dispose()
  }

  override fun updateAlwaysOnTopState() {}

  override fun updateFocusableWindowState() {}

  override fun setModalBlocked(blocker: Dialog, blocked: Boolean) {}

  override fun updateMinimumSize() {}

  override fun updateIconImages() {
    pWindow.updateIcons()
  }

  override fun setOpacity(opacity: Float) {}

  override fun setOpaque(isOpaque: Boolean) {}

  override fun updateWindow() {}

  override fun repositionSecurityWarning() {}

  override fun setBounds(x: Int, y: Int, width: Int, height: Int, op: Int) {
    super.setBounds(x, y, width, height, op)

    if (pWindow.undecorated || PWindow.windows.first() == pWindow || PGraphicsEnvironment.clientDoesWindowManagement) {  // don't change undecorated and root windows
      return
    }

    getVisibleWindowBoundsIfNeeded(x, y, width, height)?.let { pWindow.target.bounds = it }
  }

  companion object {

    fun getVisibleWindowBoundsIfNeeded(x: Int, y: Int, width: Int, height: Int): Rectangle? {
      val newBounds = Rectangle(x, y, width, height)
      val screenBounds = PGraphicsEnvironment.defaultDevice.clientScreenBounds

      if (isWindowHeaderVisibleEnough(HEADER_VISIBLE_HEIGHT_PX, windowBounds = newBounds, screenBounds = screenBounds)) {
        return null
      }

      val visibleWindowBounds = createVisibleWindowBounds(
        HEADER_VISIBLE_HEIGHT_PX,
        targetWindowBounds = newBounds,
        screenBounds = screenBounds,
      )

      if (isWindowHeaderVisibleEnough(HEADER_VISIBLE_HEIGHT_PX, windowBounds = visibleWindowBounds, screenBounds = screenBounds)) {
        return visibleWindowBounds
      }

      logger.error { "Can't create visible window bounds... ($HEADER_VISIBLE_HEIGHT_PX, $newBounds, $screenBounds)" }
      return null
    }

    fun isWindowHeaderVisibleEnough(headerVisibleHeightPx: Int, windowBounds: Rectangle, screenBounds: Rectangle): Boolean {
      val headerBounds = Rectangle(
        windowBounds.x,
        windowBounds.y - headerVisibleHeightPx,
        windowBounds.width,
        headerVisibleHeightPx
      )

      if (headerBounds.centerY.roundToInt() !in screenBounds.y..(screenBounds.y + screenBounds.height)) {
        return false
      }

      return headerBounds.centerX.roundToInt() in screenBounds.x..(screenBounds.x + screenBounds.width)
    }

    fun createVisibleWindowBounds(headerVisibleHeightPx: Int, targetWindowBounds: Rectangle, screenBounds: Rectangle): Rectangle {
      val headerBounds = Rectangle(
        targetWindowBounds.x,
        targetWindowBounds.y - headerVisibleHeightPx,
        targetWindowBounds.width,
        headerVisibleHeightPx
      )

      val visibleWindowBounds = Rectangle(targetWindowBounds)
      val visibleHeaderPoint = Point(headerBounds.centerX.roundToInt(), headerBounds.centerY.roundToInt())

      if (visibleHeaderPoint.x > screenBounds.x + screenBounds.width) {
        visibleWindowBounds.x -= visibleHeaderPoint.x - (screenBounds.x + screenBounds.width)
      }

      if (visibleHeaderPoint.y > screenBounds.y + screenBounds.height) {
        visibleWindowBounds.y -= visibleHeaderPoint.y - (screenBounds.y + screenBounds.height)
      }

      if (visibleHeaderPoint.x < screenBounds.x) {
        visibleWindowBounds.x += screenBounds.x - visibleHeaderPoint.x
      }

      if (visibleHeaderPoint.y < screenBounds.y) {
        visibleWindowBounds.y += screenBounds.y - visibleHeaderPoint.y
      }

      return visibleWindowBounds
    }

    private const val HEADER_VISIBLE_HEIGHT_PX = 10

    private val logger = Logger<PWindowPeer>()
  }
}
