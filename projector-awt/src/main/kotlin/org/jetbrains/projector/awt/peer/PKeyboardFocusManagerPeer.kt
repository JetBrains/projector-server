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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt.peer

import sun.awt.KeyboardFocusManagerPeerImpl
import java.awt.Component
import java.awt.Window
import java.awt.event.FocusEvent
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object PKeyboardFocusManagerPeer : KeyboardFocusManagerPeerImpl() {

  private var focusOwner: Component? = null

  private val lock = ReentrantReadWriteLock(true)

  private var focusedWindow: Window? = null

  override fun setCurrentFocusedWindow(window: Window) {
    lock.write {
      focusedWindow = window
    }
  }

  override fun getCurrentFocusedWindow(): Window? {
    return lock.read {
      focusedWindow
    }
  }

  override fun setCurrentFocusOwner(component: Component?) {
    lock.write {
      focusOwner = component
    }
  }

  override fun getCurrentFocusOwner(): Component? {
    return lock.read {
      focusOwner
    }
  }

  fun deliverFocus(
    lightweightChild: Component,
    target: Component,
    temporary: Boolean,
    focusedWindowChangeAllowed: Boolean,
    time: Long,
    cause: FocusEvent.Cause,
  ): Boolean {
    return deliverFocus(lightweightChild, target, temporary, focusedWindowChangeAllowed, time, cause, currentFocusOwner)
  }
}
