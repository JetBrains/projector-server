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

import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.awt.image.VolatileImage


object PGraphicsConfiguration : GraphicsConfiguration() {

  override fun getDevice(): GraphicsDevice {
    return PGraphicsDevice
  }

  override fun getColorModel(): ColorModel? {
    return ColorModel.getRGBdefault()
  }

  override fun getColorModel(transparency: Int): ColorModel? {
    return colorModel
  }

  override fun getDefaultTransform(): AffineTransform {
    return AffineTransform()
  }

  override fun getNormalizingTransform(): AffineTransform {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment() as PGraphicsEnvironment
    val xScale = ge.xResolution / 72.0
    val yScale = ge.yResolution / 72.0
    return AffineTransform(xScale, 0.0, 0.0, yScale, 0.0, 0.0)
  }

  override fun getBounds(): Rectangle {
    return Rectangle(PGraphicsDevice.clientScreenBounds)
  }

  override fun createCompatibleVolatileImage(width: Int, height: Int, caps: ImageCapabilities?, transparency: Int): VolatileImage {
    return PVolatileImage(width, height, transparency, caps)
  }
}
