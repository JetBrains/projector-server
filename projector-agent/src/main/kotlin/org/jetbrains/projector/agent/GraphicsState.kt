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

import org.jetbrains.projector.awt.image.copy
import org.jetbrains.projector.awt.image.transformShape
import sun.java2d.SunGraphics2D
import java.awt.*
import java.awt.geom.AffineTransform

internal data class GraphicsState(
  val transform: AffineTransform,
  val clip: Shape?,
  val paint: Paint,
  val background: Paint,
  val stroke: Stroke,
  val font: Font,
  val composite: Composite,
  val hints: RenderingHints?,
) {
  companion object {
    fun extractFromGraphics(g: SunGraphics2D, constrainX: Int = 0, constrainY: Int = 0) =
      GraphicsState(
        extractTransformFromGraphics(g, constrainX, constrainY),
        extractClipFromGraphics(g, constrainX, constrainY),
        g.paint,
        g.background,
        g.stroke ?: BasicStroke(),
        g.font,
        g.composite ?: AlphaComposite.SrcOver,
        g.hints?.clone() as RenderingHints?
      )

    private fun extractTransformFromGraphics(g: SunGraphics2D, constrainX: Int = 0, constrainY: Int = 0): AffineTransform {
      val transform = AffineTransform()
      transform.translate(constrainX.toDouble(), constrainY.toDouble())
      if (g.transform != null) {
        transform.scale(
          1 / g.surfaceData.defaultScaleX,
          1 / g.surfaceData.defaultScaleY
        )
        transform.concatenate(g.getTransform())
      }
      return transform
    }

    private fun extractClipFromGraphics(g: SunGraphics2D, constrainX: Int = 0, constrainY: Int = 0): Shape? {
      val transform = extractTransformFromGraphics(g, constrainX, constrainY)
      return g.clip?.copy()?.transformShape(transform)
    }
  }
}
