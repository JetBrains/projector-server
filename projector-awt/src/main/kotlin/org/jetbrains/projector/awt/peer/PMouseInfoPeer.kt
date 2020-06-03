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
import java.awt.Point
import java.awt.Window
import java.awt.peer.MouseInfoPeer

object PMouseInfoPeer : MouseInfoPeer {

  private const val PRIMARY_SCREEN_DEVICE_ID = 0

  val lastMouseCoords = Point()

  override fun fillPointWithCoords(point: Point): Int {
    point.location = lastMouseCoords
    return PRIMARY_SCREEN_DEVICE_ID
  }

  override fun isWindowUnderMouse(w: Window): Boolean {
    return PWindow.findWindowAt(lastMouseCoords.x, lastMouseCoords.y)?.target == w
  }
}
