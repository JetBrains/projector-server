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
package org.jetbrains.projector.awt.image

import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.awt.image.VolatileImage


class PGraphicsConfiguration(private val device: PGraphicsDevice) : GraphicsConfiguration() {

  override fun getDevice(): GraphicsDevice {
    return device
  }

  override fun getColorModel(): ColorModel? {
    return ColorModel.getRGBdefault()
  }

  override fun getColorModel(transparency: Int): ColorModel? {
    return colorModel
  }

  override fun getDefaultTransform(): AffineTransform {
    return AffineTransform.getScaleInstance(device.scaleFactor, device.scaleFactor)
  }

  override fun getNormalizingTransform(): AffineTransform {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment() as PGraphicsEnvironment
    val xScale = ge.xResolution / 72.0
    val yScale = ge.yResolution / 72.0
    return AffineTransform(xScale, 0.0, 0.0, yScale, 0.0, 0.0)
  }

  override fun getBounds(): Rectangle {
    return Rectangle(device.clientScreenBounds)
  }

  override fun createCompatibleVolatileImage(width: Int, height: Int, caps: ImageCapabilities?, transparency: Int): VolatileImage {
    return PVolatileImage(width, height, transparency, caps)
  }
}
