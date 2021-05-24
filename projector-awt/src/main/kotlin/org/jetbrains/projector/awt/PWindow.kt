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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package org.jetbrains.projector.awt

import org.jetbrains.projector.awt.data.Direction
import org.jetbrains.projector.awt.image.PGraphics2D
import org.jetbrains.projector.awt.image.PGraphicsEnvironment
import org.jetbrains.projector.awt.peer.PWindowPeer.Companion.getVisibleWindowBoundsIfNeeded
import org.jetbrains.projector.awt.service.ImageCacher
import sun.awt.AWTAccessor
import java.awt.*
import java.awt.event.ComponentEvent
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.abs
import kotlin.math.min

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

  init {
    updateGraphics()
  }

  fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val hasMoved = target.x != x || target.y != y
    val hasResized = target.width != width || target.height != height

    if (!hasMoved && !hasResized) return

    toFront()
    target.requestFocusInWindow()

    if (PGraphicsEnvironment.clientDoesWindowManagement) {
      AWTAccessor.getComponentAccessor().setLocation(target, x, y)
      AWTAccessor.getComponentAccessor().setSize(target, width, height)
    }
    else {
      val visibleBounds = getVisibleWindowBoundsIfNeeded(x, y, width, height)

      if (visibleBounds == null) {
        AWTAccessor.getComponentAccessor().setLocation(target, x, y)
        AWTAccessor.getComponentAccessor().setSize(target, width, height)
      }
      else {
        AWTAccessor.getComponentAccessor().setLocation(target, visibleBounds.x, visibleBounds.y)
        AWTAccessor.getComponentAccessor().setSize(target, visibleBounds.width, visibleBounds.height)
      }
    }

    updateGraphics()

    if (hasMoved)
      (target as? Window)?.dispatchEvent(ComponentEvent(target, ComponentEvent.COMPONENT_MOVED))
    if (hasResized)
      (target as? Window)?.dispatchEvent(ComponentEvent(target, ComponentEvent.COMPONENT_RESIZED))

    // We need to trigger layout recalculation manually.
    if (hasResized) target.revalidate()
    repaint()
  }

  private fun updateGraphics() {
    val windowMidpoint = with(target.bounds) { Point(x + width / 2, y + height / 2) }
    val newDevice = PGraphicsEnvironment.devices.minByOrNull {
      val deviceBounds = it.bounds
      if(deviceBounds.contains(windowMidpoint)) 0 else {
        min(min(abs(deviceBounds.x - windowMidpoint.x), abs(deviceBounds.x + deviceBounds.width - windowMidpoint.x)),
            min(abs(deviceBounds.y - windowMidpoint.y), abs(deviceBounds.y + deviceBounds.height - windowMidpoint.y)))
      }
    } ?: return
    graphics.device = newDevice
  }

  fun move(deltaX: Int, deltaY: Int) {
    setBounds(target.x + deltaX, target.y + deltaY, target.width, target.height)
  }

  fun resize(deltaX: Int, deltaY: Int, direction: Direction) {
    if (direction == Direction.E || direction == Direction.S || direction == Direction.SE) {
      setBounds(target.x, target.y, target.size.width + deltaX, target.size.height + deltaY)
    }
    else if (direction == Direction.SW) {
      setBounds(target.x + deltaX, target.y, target.width - deltaX, target.size.height + deltaY)
    }
    else if (direction == Direction.NE) {
      setBounds(target.x, target.y + deltaY, target.width + deltaX, target.height - deltaY)
    }
    else {
      setBounds(target.x + deltaX, target.y + deltaY, target.width - deltaX, target.height - deltaY)
    }
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
