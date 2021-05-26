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

package org.jetbrains.projector.agent

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.peer.PMouseInfoPeer
import org.jetbrains.projector.common.protocol.toClient.ServerDrawCommandsEvent
import org.jetbrains.projector.common.protocol.toClient.ServerWindowEvent
import org.jetbrains.projector.server.ProjectorServer
import org.jetbrains.projector.server.core.util.unprotect
import org.jetbrains.projector.server.service.ProjectorDrawEventQueue
import org.jetbrains.projector.server.service.ProjectorFontProvider
import org.jetbrains.projector.util.logging.Logger
import sun.awt.NullComponentPeer
import sun.java2d.SunGraphics2D
import java.awt.*
import java.awt.peer.ComponentPeer
import javax.swing.JComponent

internal object GraphicsInterceptor {
  private var commands = mutableListOf<ServerWindowEvent>()

  private var paintToOffscreenInProgress = false

  // constraint in terms of SunGraphics2d: see constrainX/constrainY
  // this is offset of the repainting area
  private lateinit var paintingConstraint: Point

  private var expectNewVolatileImageGraphics = false
  private var expectNewBufferedImageGraphics = false
  private var imageGraphicsIds: HashSet<Int> = HashSet()

  private val pWindows = mutableMapOf<Int, PWindow>()
  private val queues = mutableMapOf<Int, ProjectorDrawEventQueue>()
  private var currentQueue: ProjectorDrawEventQueue? = null

  @Suppress("unused")
  private val server = ProjectorServer.startServer(isAgent = true) {
    // todo: make it work with dynamic agent
    //setupAgentSystemProperties()
    //setupAgentSingletons()
    ProjectorFontProvider.isAgent = true
  }

  @Suppress("unused", "PLATFORM_CLASS_MAPPED_TO_KOTLIN",
            "UNUSED_PARAMETER")  // Integer is needed because this function is used via reflection
  @JvmStatic
  fun beginPaintToOffscreen(comp: JComponent, x: Integer, y: Integer, w: Integer, h: Integer) {
    paintToOffscreenInProgress = true

    val parentWindow = getParentWindow(comp)
    val pWindow = pWindows.getOrPut(parentWindow.id()) { PWindow(parentWindow, isAgent = true) }

    currentQueue = queues.getOrPut(parentWindow.id()) {
      ProjectorDrawEventQueue.create(ServerDrawCommandsEvent.Target.Onscreen(pWindow.id))
    }

    paintingConstraint = calculateComponentPositionInsideWindow(comp, parentWindow).let {
      Point(
        it.x + x.toInt(),
        it.y + y.toInt()
      )
    }

    expectNewVolatileImageGraphics = false
    expectNewBufferedImageGraphics = false
    imageGraphicsIds = HashSet()
  }

  private fun calculateComponentPositionInsideWindow(component: JComponent, window: Component): Point {
    var x = component.x
    var y = component.y
    var parent = component.parent
    while (parent != null && parent != window) {
      x += parent.x
      y += parent.y
      parent = parent.parent
    }

    return Point(x, y)
  }

  @Suppress("unused")
  @JvmStatic
  fun getClientList(): Array<Array<String?>> {
    return server.getClientList()
  }

  @Suppress("unused")
  @JvmStatic
  fun disconnectAll() {
    server.disconnectAll()
  }

  @Suppress("unused")
  @JvmStatic
  fun disconnectByIp(ip: String) {
    server.disconnectByIp(ip)
  }

  @Suppress("unused")
  @JvmStatic
  fun endPaintToOffscreen() {
    currentQueue?.commands?.add(commands) ?: logger.debug { "currentQueue == null" }
    commands = mutableListOf()
    paintToOffscreenInProgress = false
    currentQueue = null
  }

  @Suppress("unused")
  @JvmStatic
  fun startInitBalloonImage() {
    paintToOffscreenInProgress = false
  }

  @Suppress("unused")
  @JvmStatic
  fun endInitBalloonImage() {
    paintToOffscreenInProgress = true
  }

  @Suppress("unused")
  @JvmStatic
  fun sunVolatileImageCreateGraphics() {
    expectNewVolatileImageGraphics = true
  }

  @Suppress("unused")
  @JvmStatic
  fun bufferedImageCreateGraphics() {
    expectNewBufferedImageGraphics = true
  }

  @Suppress("unused")
  @JvmStatic
  fun handleGraphics2D(methodName: String, args: Array<Any?>, g: Graphics) {
    if (checkForImageDrawing(g.id())) {
      return
    }

    if (paintToOffscreenInProgress) {
      val graphicsState = GraphicsState.extractFromGraphics(g as SunGraphics2D, paintingConstraint.x, paintingConstraint.y)

      CommandsHandler.createServerWindowEvents(methodName, copyArgs(args), graphicsState)
        .run(commands::addAll)
    }
  }

  private fun checkForImageDrawing(id: Int): Boolean {
    if (expectNewVolatileImageGraphics || expectNewBufferedImageGraphics) {
      imageGraphicsIds.add(id)
      expectNewVolatileImageGraphics = false
      expectNewBufferedImageGraphics = false
      return true
    }

    return id in imageGraphicsIds
  }

  @Suppress("unused")
  @JvmStatic
  fun handleUpdateCursorImmediately(comp: Component) {
    if (!comp.isShowing) {
      return
    }
    val window = getParentWindow(comp)
    if (pWindows.contains(window.id())) {
      val location = window.locationOnScreen
      val mouseLocation = PMouseInfoPeer.lastMouseCoords
      val targetComp = (window as Container).findComponentAt(mouseLocation - location) ?: return
      pWindows[window.id()]!!.cursor = targetComp.cursor
    }
  }

  private fun getParentWindow(comp: Component): Component {
    var currComp = comp
    val peerField = Component::class.java.getDeclaredField("peer")
    peerField.unprotect()
    var peer = peerField.get(currComp) as ComponentPeer
    while (peer is NullComponentPeer) {
      currComp = currComp.parent
      peer = peerField.get(currComp) as ComponentPeer
    }

    return currComp
  }

  private fun copyArgs(args: Array<Any?>): Array<Any?> {
    return args.map {
      when (it) {
        is Rectangle -> it.clone()
        else -> it
      }
    }.toTypedArray()
  }

  private fun Component.id(): Int {
    return System.identityHashCode(this)
  }

  private fun Graphics.id(): Int {
    return System.identityHashCode(this)
  }

  private operator fun Point.minus(other: Point) = Point(x - other.x, y - other.y)

  private val logger = Logger<GraphicsInterceptor>()
}
