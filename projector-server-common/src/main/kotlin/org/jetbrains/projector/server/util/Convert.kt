/*
 * Copyright (c) 2019-2024, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

package org.jetbrains.projector.server.util

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.data.AwtImageInfo
import org.jetbrains.projector.awt.data.AwtPaintType
import org.jetbrains.projector.awt.data.Direction
import org.jetbrains.projector.common.protocol.data.ImageEventInfo
import org.jetbrains.projector.common.protocol.data.PaintType
import org.jetbrains.projector.common.protocol.toClient.WindowClass
import org.jetbrains.projector.common.protocol.toClient.WindowType
import org.jetbrains.projector.common.protocol.toServer.ResizeDirection
import java.awt.Dialog
import java.awt.Frame
import java.awt.Window
import javax.swing.JWindow

fun AwtPaintType.toPaintType() = when (this) {
  AwtPaintType.DRAW -> PaintType.DRAW
  AwtPaintType.FILL -> PaintType.FILL
}

fun AwtImageInfo.toImageEventInfo() = when (this) {
  is AwtImageInfo.Point -> ImageEventInfo.Xy(x = x, y = y)
  is AwtImageInfo.Rectangle -> ImageEventInfo.XyWh(x = x, y = y, width = width, height = height, argbBackgroundColor = argbBackgroundColor)
  is AwtImageInfo.Area -> ImageEventInfo.Ds(
    dx1 = dx1, dy1 = dy1, dx2 = dx2, dy2 = dy2,
    sx1 = sx1, sy1 = sy1, sx2 = sx2, sy2 = sy2,
    argbBackgroundColor = argbBackgroundColor
  )
  is AwtImageInfo.Transformation -> ImageEventInfo.Transformed(tx)
}

val PWindow.windowType: WindowType
  get() = when {
    "IdeFrameImpl" in target::class.java.simpleName -> WindowType.IDEA_WINDOW
    target.let { it is Window && it.type == Window.Type.POPUP } -> WindowType.POPUP
    else -> WindowType.WINDOW
  }

val PWindow.windowClass: WindowClass
  get() = when (target) {
    is Frame -> WindowClass.FRAME
    is Dialog -> WindowClass.DIALOG
    is JWindow -> WindowClass.JWINDOW
    else -> WindowClass.OTHER
  }

fun ResizeDirection.toDirection() = when (this) {
  ResizeDirection.NW -> Direction.NW
  ResizeDirection.SW -> Direction.SW
  ResizeDirection.NE -> Direction.NE
  ResizeDirection.SE -> Direction.SE
  ResizeDirection.N -> Direction.N
  ResizeDirection.W -> Direction.W
  ResizeDirection.S -> Direction.S
  ResizeDirection.E -> Direction.E
}
