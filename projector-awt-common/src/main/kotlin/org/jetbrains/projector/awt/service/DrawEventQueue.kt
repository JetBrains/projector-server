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
package org.jetbrains.projector.awt.service

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.data.AwtImageInfo
import org.jetbrains.projector.awt.data.AwtPaintType
import org.jetbrains.projector.awt.image.PVolatileImage
import java.awt.*

interface DrawEventQueue {

  fun buildCommand(): CommandBuilder


  // equals & hashCode: default because we don't allow collisions in IDs

  companion object {

    lateinit var createOnScreen: (PWindow.Descriptor) -> DrawEventQueue

    lateinit var createOffScreen: (PVolatileImage.Descriptor) -> DrawEventQueue
  }

  interface CommandBuilder {

    fun setClip(identitySpaceClip: Shape?): CommandBuilder
    fun setTransform(tx: List<Double>): CommandBuilder
    fun setStroke(stroke: Stroke): CommandBuilder
    fun setPaint(paint: Paint): CommandBuilder
    fun setComposite(composite: Composite): CommandBuilder
    fun setFont(font: Font): CommandBuilder

    fun drawRenderedImage()
    fun drawRenderableImage()
    fun drawString(string: String, x: Double, y: Double, desiredWidth: Double)
    fun copyArea(x: Int, y: Int, width: Int, height: Int, dx: Int, dy: Int)
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int)
    fun paintRect(paintType: AwtPaintType, x: Double, y: Double, width: Double, height: Double)
    fun paintRoundRect(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int)
    fun paintOval(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int)
    fun paintArc(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int)
    fun drawPolyline(points: List<Pair<Int, Int>>)
    fun paintPolygon(paintType: AwtPaintType, points: List<Pair<Int, Int>>)
    fun drawImage(imageId: Any, awtImageInfo: AwtImageInfo)
    fun paintPath(paintType: AwtPaintType, path: Shape)
  }
}
