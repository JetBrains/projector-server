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

import java.awt.Frame
import java.awt.MenuBar
import java.awt.Rectangle
import java.awt.peer.FramePeer

class PFramePeer(target: Frame) : PWindowPeer(target), FramePeer {

  override fun setTitle(title: String?) {
    pWindow.title = title
  }

  override fun setMenuBar(mb: MenuBar) {}

  override fun setResizable(resizeable: Boolean) {}

  override fun setState(state: Int) {}

  override fun getState(): Int {
    return 0
  }

  override fun setMaximizedBounds(bounds: Rectangle?) {}

  override fun setBoundsPrivate(x: Int, y: Int, width: Int, height: Int) {}

  override fun getBoundsPrivate(): Rectangle? {
    return null
  }

  override fun emulateActivation(b: Boolean) {}
}
