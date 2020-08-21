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
package org.jetbrains.projector.awt.data

sealed class AwtImageInfo {

  data class Point(val x: Int, val y: Int, val argbBackgroundColor: Int?) : AwtImageInfo()
  data class Rectangle(val x: Int, val y: Int, val width: Int, val height: Int, val argbBackgroundColor: Int?) : AwtImageInfo()
  data class Area(
    val dx1: Int, val dy1: Int, val dx2: Int, val dy2: Int,
    val sx1: Int, val sy1: Int, val sx2: Int, val sy2: Int,
    val argbBackgroundColor: Int?,
  ) : AwtImageInfo()

  data class Transformation(val tx: List<Double>) : AwtImageInfo()
}
