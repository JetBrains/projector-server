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

package org.jetbrains.projector.awt.peer.base

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.PWindowUtils
import org.jetbrains.projector.awt.image.PGraphicsEnvironment
import org.jetbrains.projector.awt.peer.PKeyboardFocusManagerPeer
import java.awt.Dialog
import java.awt.Window
import java.awt.peer.WindowPeer

abstract class PWindowPeerBase(target: Window) : PContainerPeerBase(target), WindowPeer {

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
      targetOwner = PWindow.windows.filter { it !== pWindow }.mapNotNull { it.target as? Window }.lastOrNull(Window::isFocusableWindow)
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

    if (pWindow.undecorated || PWindow.windows.first() == pWindow || PGraphicsEnvironment.clientDoesWindowManagement) {
      return // don't change undecorated and root windows
    }

    PWindowUtils.getVisibleWindowBoundsIfNeeded(x, y, width, height)?.let { pWindow.target.bounds = it }
  }
}
