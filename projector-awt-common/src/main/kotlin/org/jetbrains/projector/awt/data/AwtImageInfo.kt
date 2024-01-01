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
