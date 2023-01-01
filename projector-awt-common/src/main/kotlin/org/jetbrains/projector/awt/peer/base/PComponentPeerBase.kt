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

package org.jetbrains.projector.awt.peer.base

import org.jetbrains.projector.awt.PToolkitBase
import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.image.PVolatileImage
import sun.awt.PaintEventDispatcher
import org.jetbrains.projector.awt.peer.PKeyboardFocusManagerPeer
import org.jetbrains.projector.awt.peer.PMouseInfoPeer
import sun.java2d.pipe.Region
import java.awt.*
import java.awt.BufferCapabilities.FlipContents
import java.awt.dnd.DropTarget
import java.awt.dnd.peer.DropTargetPeer
import java.awt.event.ComponentEvent
import java.awt.event.FocusEvent
import java.awt.event.PaintEvent
import java.awt.image.ColorModel
import java.awt.image.VolatileImage
import java.awt.peer.ComponentPeer
import java.awt.peer.ContainerPeer

abstract class PComponentPeerBase(target: Component, private val isFocusable: Boolean = false) : ComponentPeer, DropTargetPeer {

  private val toolkit: Toolkit
    get() = Toolkit.getDefaultToolkit()

  val pWindow = PWindow(target, isAgent = false)
  private var myGraphicsConfiguration: GraphicsConfiguration? = null

  override fun dispose() {
    PToolkitBase.disposePeer(this)
    pWindow.dispose()
  }

  override fun addDropTarget(dt: DropTarget) {}

  override fun removeDropTarget(dt: DropTarget) {}

  override fun isObscured(): Boolean {
    // false because canDetermineObscurity indicates we do not support this
    return false
  }

  override fun canDetermineObscurity(): Boolean {
    return false
  }

  override fun setVisible(v: Boolean) {
    if (v) {
      // like XWindow.postPaintEvent does: without it, popups will be initially transparent when shown (PRJ-552)
      val paintEvent = PaintEventDispatcher
        .getPaintEventDispatcher()
        .createPaintEvent(pWindow.target, 0, 0, pWindow.target.width, pWindow.target.height)

      paintEvent?.let { PToolkitBase.systemEventQueueImplPP.postEvent(it) }
    }

    pWindow.target.isVisible = v
  }

  override fun setEnabled(e: Boolean) {
    pWindow.target.isEnabled = e
  }

  override fun paint(g: Graphics) {
    // todo: paint peer

    pWindow.target.paint(g)
  }

  override fun print(g: Graphics) {
    g.color = pWindow.target.background
    g.fillRect(0, 0, pWindow.target.width, pWindow.target.height)
    g.color = pWindow.target.foreground

    // todo: paint peer

    pWindow.target.print(g)
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int, op: Int) {
    fun dispatchIfNeeded(eventId: Int) {
      (pWindow.target as? Window)?.dispatchEvent(ComponentEvent(pWindow.target, eventId))
    }

    when (op) {
      ComponentPeer.SET_BOUNDS -> {
        dispatchIfNeeded(ComponentEvent.COMPONENT_MOVED)
        dispatchIfNeeded(ComponentEvent.COMPONENT_RESIZED)
      }
      ComponentPeer.SET_LOCATION -> dispatchIfNeeded(ComponentEvent.COMPONENT_MOVED)
      ComponentPeer.SET_SIZE, ComponentPeer.SET_CLIENT_SIZE -> dispatchIfNeeded(ComponentEvent.COMPONENT_RESIZED)
    }
  }

  override fun handleEvent(e: AWTEvent) {}

  override fun coalescePaintEvent(e: PaintEvent) {}

  override fun getLocationOnScreen(): Point {
    return pWindow.target.location
  }

  override fun getPreferredSize(): Dimension {
    return minimumSize
  }

  override fun getMinimumSize(): Dimension {
    return pWindow.target.size
  }

  override fun getColorModel(): ColorModel {
    return Toolkit.getDefaultToolkit().colorModel
  }

  override fun getGraphics(): Graphics {
    return pWindow.graphics.create()
  }

  override fun getFontMetrics(font: Font): FontMetrics {
    @Suppress("DEPRECATION")  // todo
    return toolkit.getFontMetrics(font)
  }

  override fun setForeground(c: Color) {
  }

  override fun setBackground(c: Color) {
  }

  override fun setFont(f: Font) {
  }

  override fun updateCursorImmediately() {
    // todo: delegate to GlobalCursorManager (like in XComponentPeer)
    val mousePoint = PMouseInfoPeer.lastMouseCoords
    val containerUnderMouse = PMouseInfoPeer.lastWindowUnderMouse as? Container

    val cursorUnderMouse = containerUnderMouse?.let {
      val location = it.location
      val componentUnderMouse: Component? = it.findComponentAt(mousePoint.x - location.x, mousePoint.y - location.y)
      componentUnderMouse?.cursor
    }

    pWindow.cursor = cursorUnderMouse
  }

  override fun requestFocus(
    lightweightChild: Component,
    temporary: Boolean,
    focusedWindowChangeAllowed: Boolean,
    time: Long,
    cause: FocusEvent.Cause,
  ): Boolean {
    pWindow.target.let {
      return PKeyboardFocusManagerPeer.deliverFocus(
        lightweightChild,
        it,
        temporary,
        focusedWindowChangeAllowed,
        time,
        cause
      )
    }
  }

  override fun isFocusable() = isFocusable

  override fun createImage(width: Int, height: Int): Image {
    return PVolatileImage(width, height)
  }

  override fun createVolatileImage(width: Int, height: Int): VolatileImage {
    return PVolatileImage(width, height)
  }

  override fun getGraphicsConfiguration(): GraphicsConfiguration {
    return myGraphicsConfiguration ?: pWindow.target.graphicsConfiguration
  }

  override fun handlesWheelScrolling(): Boolean {
    return false
  }

  override fun createBuffers(numBuffers: Int, caps: BufferCapabilities) {}

  override fun getBackBuffer(): Image {
    throw IllegalStateException("Buffers have not been created")
  }

  override fun flip(x1: Int, y1: Int, x2: Int, y2: Int, flipAction: FlipContents) {}

  override fun destroyBuffers() {}

  override fun reparent(newContainer: ContainerPeer) {}

  override fun isReparentSupported(): Boolean {
    return false
  }

  override fun layout() {}

  override fun applyShape(shape: Region?) {}

  override fun setZOrder(above: ComponentPeer?) {}

  override fun updateGraphicsData(gc: GraphicsConfiguration): Boolean {
    myGraphicsConfiguration = gc
    return false
  }
}
