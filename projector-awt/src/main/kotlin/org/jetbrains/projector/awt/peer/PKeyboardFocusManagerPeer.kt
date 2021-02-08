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

import sun.awt.KeyboardFocusManagerPeerImpl
import java.awt.Component
import java.awt.Window
import java.awt.event.FocusEvent
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object PKeyboardFocusManagerPeer : KeyboardFocusManagerPeerImpl() {

  private var focusOwner: Component? = null

  private val lock = ReentrantReadWriteLock(true)

  private var focusedWindow: Window? = null

  /**
   * In a recent build of JetBrains runtime, the signature changed from
   *      boolean deliverFocus(Component lightweightChild, Component target,
   *                        boolean temporary, boolean focusedWindowChangeAllowed, long time,
   *                        FocusEvent.Cause cause, Component currentFocusOwner)
   * to
   *      boolean deliverFocus(Component lightweightChild, Component target,
   *                        boolean highPriority,
   *                        FocusEvent.Cause cause, Component currentFocusOwner)
   *
   * https://github.com/JetBrains/JetBrainsRuntime/commit/1a9838082e3eb48d43e6bac6a412463923173fc7#diff-5818ad29e3f2e395597b8565f3553ad139de16439414e3fc688412fb35bd57f4
   */
  private val deliverFocusMethod: Method
  private val newDeliverFocusMethod: Boolean

  init {
    val result = try {
      KeyboardFocusManagerPeerImpl::class.java.getMethod("deliverFocus",
        Component::class.java, Component::class.java,
        Boolean::class.java, Boolean::class.java, Long::class.java,
        FocusEvent.Cause::class.java, Component::class.java) to false
    } catch (e: NoSuchMethodException) {
      KeyboardFocusManagerPeerImpl::class.java.getMethod("deliverFocus",
        Component::class.java, Component::class.java,
        Boolean::class.java,
        FocusEvent.Cause::class.java, Component::class.java) to true
    }

    deliverFocusMethod = result.first
    newDeliverFocusMethod = result.second
  }

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
    // JetBrains Runtime passes "false" on X11, we're doing the same here
    // https://github.com/JetBrains/JetBrainsRuntime/blob/1a9838082e3eb48d43e6bac6a412463923173fc7/src/java.desktop/unix/classes/sun/awt/X11/XKeyboardFocusManagerPeer.java#L108

    return when (newDeliverFocusMethod) {
      true -> deliverFocusMethod.invoke(null, lightweightChild, target, false, cause, currentFocusOwner)
      false -> deliverFocusMethod.invoke(null, lightweightChild, target, temporary, focusedWindowChangeAllowed, time, cause, currentFocusOwner)
    } as Boolean
  }
}
