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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt.peer

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.image.PGraphicsDevice
import org.jetbrains.projector.awt.service.Logger
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

    if (pWindow.undecorated || PWindow.windows.first() == pWindow) {  // don't change undecorated and root windows
      return
    }

    val newBounds = Rectangle(x, y, width, height)
    val screenBounds = PGraphicsDevice.clientScreenBounds

    if (!isWindowHeaderVisibleEnough(
        HEADER_VISIBLE_HEIGHT_PX,
        windowBounds = newBounds,
        screenBounds = screenBounds
      )
    ) {
      val visibleWindowBounds =
        createVisibleWindowBounds(
          HEADER_VISIBLE_HEIGHT_PX,
          targetWindowBounds = newBounds,
          screenBounds = screenBounds
        )

      if (!isWindowHeaderVisibleEnough(
          HEADER_VISIBLE_HEIGHT_PX,
          windowBounds = visibleWindowBounds,
          screenBounds = screenBounds
        )
      ) {
        logger.error { "Can't create visible window bounds... ($HEADER_VISIBLE_HEIGHT_PX, $newBounds, $screenBounds)" }
        return
      }

      pWindow.target.bounds = visibleWindowBounds
    }
  }

  companion object {

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

    private val logger = Logger.factory(PWindowPeer::class.java)
  }
}
