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
package org.jetbrains.projector.awt.image

import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.GeneralPath
import java.awt.geom.Rectangle2D

/*
 * Intersect two Shapes by the simplest method, attempting to produce
 * a simplified result.
 * The boolean arguments keep1 and keep2 specify whether or not
 * the first or second shapes can be modified during the operation
 * or whether that shape must be "kept" unmodified.
 */
fun intersectShapes(s1: Shape, s2: Shape, keep1: Boolean, keep2: Boolean): Shape {
  return when {
    s1 is Rectangle2D && s2 is Rectangle2D -> s1.createIntersection(s2)

    s1 is Rectangle2D -> intersectRectShape(s1, s2, keep1, keep2)

    s2 is Rectangle2D -> intersectRectShape(s2, s1, keep2, keep1)

    else -> intersectByArea(s1, s2, keep1, keep2)
  }
}

/*
 * Intersect a Rectangle with a Shape by the simplest method,
 * attempting to produce a simplified result.
 * The boolean arguments keepR and keepS specify whether or not
 * the first or second shapes can be modified during the operation
 * or whether that shape must be "kept" unmodified.
 */
fun intersectRectShape(
  r: Rectangle2D,
  s: Shape,
  keepR: Boolean,
  keepS: Boolean
): Shape {
  if (r.contains(s.bounds2D)) {
    if (keepS) {
      return GeneralPath(s)
    }
    return s
  }
  return intersectByArea(r, s, keepR, keepS)
}

/*
 * Intersect two Shapes using the Area class. Presumably other
 * attempts at simpler intersection methods proved fruitless.
 * The boolean arguments keep1 and keep2 specify whether or not
 * the first or second shapes can be modified during the operation
 * or whether that shape must be "kept" unmodified.
 * @see #intersectShapes
 * @see #intersectRectShape
 */
private fun intersectByArea(s1: Shape, s2: Shape, keep1: Boolean, keep2: Boolean): Shape {
  // First see if we can find an overwriteable source shape
  // to use as our destination area to avoid duplication.
  val (a1: Area, remainingShape: Shape) = when {
    !keep1 && s1 is Area -> s1 to s2
    !keep2 && s2 is Area -> s2 to s1
    else -> Area(s1) to s2
  }

  val a2: Area = remainingShape as? Area ?: Area(remainingShape)

  a1.intersect(a2)

  return if (a1.isRectangular) a1.bounds else a1
}

fun Shape.copy(): Shape {
  return when (this) {
    is Rectangle2D -> Rectangle2D.Double(x, y, width, height)

    else -> GeneralPath(this)
  }
}
