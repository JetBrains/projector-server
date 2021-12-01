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
package org.jetbrains.projector.awt.image

import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.GeneralPath
import java.awt.geom.Rectangle2D

fun AffineTransform.toList(): List<Double> {
  return DoubleArray(6).also { this.getMatrix(it) }.toList()
}

private val AffineTransform.hasNoRotation: Boolean get() = shearX == 0.0 && shearY == 0.0

fun Shape.transformShape(tx: AffineTransform): Shape {
  return when {
    this is Rectangle2D && tx.hasNoRotation -> {
      val points = doubleArrayOf(
        this.x, this.y,
        this.x + this.width, this.y + this.height
      )

      tx.transform(points, 0, points, 0, 2)

      Rectangle2D.Double(
        points[0],
        points[1],
        points[2] - points[0],
        points[3] - points[1]
      )
    }

    // this is Rectangle2D && tx.hasSwappedCoordinates -> TODO: it's a rectangle too

    tx.isIdentity -> GeneralPath(this)

    else -> tx.createTransformedShape(this)
  }
}
