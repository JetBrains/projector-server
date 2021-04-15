/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import org.jetbrains.projector.awt.data.AwtComponentAccessor
import org.jetbrains.projector.awt.data.Direction
import org.jetbrains.projector.awt.image.PGraphics2D
import org.jetbrains.projector.awt.service.ImageCacher
import java.awt.*
import java.awt.event.ComponentEvent
import java.lang.ref.WeakReference
import java.util.*

class PWindow(val target: Component) {

  val id: Int

  var title: String? = when (target) {
    is Frame -> target.title
    is Dialog -> target.title
    else -> null
  }

  val resizable: Boolean = when (target) {
    is Frame -> target.isResizable
    is Dialog -> target.isResizable
    else -> false
  }

  val modal: Boolean = when (target) {
    is Dialog -> target.isModal
    else -> false
  }

  /** If true, window has no border and header (Popups in Idea main menu are usually undecorated). */
  val undecorated: Boolean = when (target) {
    is Frame -> target.isUndecorated
    is Dialog -> target.isUndecorated
    else -> true
  }

  /** ImageIds of icons. */
  var icons: List<Any>? = null
    private set

  val headerHeight: Int?
    get() = when (target) {
      is Container -> target.insets?.top ?: 0
      else -> null
    }

  init {
    updateIcons()
  }

  private val self by lazy { WeakReference(this) }

  var cursor: Cursor? = target.cursor

  init {
    synchronized(weakWindows) {
      val usedIds = weakWindows.mapNotNull { it.get()?.id }.toSet()
      id = generateSequence(0) { 1 + it }.first { it !in usedIds }

      weakWindows.addLast(self)
    }
  }

  val graphics = PGraphics2D(target, Descriptor(id))

  fun move(deltaX: Int, deltaY: Int) {
    toFront()
    target.requestFocusInWindow()
    AwtComponentAccessor.setLocation(target, target.x + deltaX, target.y + deltaY)
    (target as? Window)?.dispatchEvent(ComponentEvent(target, ComponentEvent.COMPONENT_MOVED))
    repaint()
  }

  fun resize(deltaX: Int, deltaY: Int, direction: Direction) {
    toFront()
    target.requestFocusInWindow()

    fun setRawBounds(x: Int, y: Int, width: Int, height: Int) {
      AwtComponentAccessor.setBounds(target, x, y, width, height)
      (target as? Window)?.dispatchEvent(ComponentEvent(target, ComponentEvent.COMPONENT_MOVED))
      (target as? Window)?.dispatchEvent(ComponentEvent(target, ComponentEvent.COMPONENT_RESIZED))
    }

    if (direction == Direction.E || direction == Direction.S || direction == Direction.SE) {
      setRawBounds(target.x, target.y, target.size.width + deltaX, target.size.height + deltaY)
    }
    else if (direction == Direction.SW) {
      setRawBounds(target.x + deltaX, target.y, target.width - deltaX, target.size.height + deltaY)
    }
    else if (direction == Direction.NE) {
      setRawBounds(target.x, target.y + deltaY, target.width + deltaX, target.height - deltaY)
    }
    else {
      setRawBounds(target.x + deltaX, target.y + deltaY, target.width - deltaX, target.height - deltaY)
    }

    // We need to trigger layout recalculation manually.
    target.revalidate()
    repaint()
  }

  fun close() {
    (target as? Window)?.dispose()
  }

  fun repaint() {
    target.repaint()
  }

  fun updateIcons() {
    icons = (target as? Window)?.iconImages?.map { ImageCacher.instance.getImageId(it, "updateIcons") }
  }

  fun toFront() {
    synchronized(weakWindows) {
      if (weakWindows.last != self) {
        weakWindows.remove(self)
        weakWindows.addLast(self)
      }
    }
  }

  fun toBack() {
    synchronized(weakWindows) {
      if (weakWindows.first != self) {
        weakWindows.remove(self)
        weakWindows.addFirst(self)
      }
    }
  }

  fun dispose() {
    disposeWindow(this)
  }

  companion object {

    private var weakWindows = ArrayDeque<WeakReference<PWindow>>()  // first is back and last is front

    /** All windows on screen. First is back and last is front. */
    val windows: List<PWindow>
      get() = synchronized(weakWindows) {
        val windows = weakWindows.mapNotNull(WeakReference<PWindow>::get)

        if (weakWindows.size != windows.size) {
          weakWindows = ArrayDeque(windows.map(::WeakReference))
        }

        windows
      }

    private fun disposeWindow(window: PWindow) {
      synchronized(weakWindows) {
        weakWindows.removeIf { it.get() == window }
      }
    }

    fun getWindow(windowId: Int): PWindow? = windows.find { it.id == windowId }
  }

  class Descriptor(val windowId: Int)
}
