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
