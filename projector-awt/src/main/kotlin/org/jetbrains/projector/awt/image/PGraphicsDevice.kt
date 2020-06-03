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

import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Point
import java.awt.Rectangle

object PGraphicsDevice : GraphicsDevice() {

  private const val idString: String = "Display0"

  val bounds = Rectangle(1024, 768)
  val clientShift = Point(0, 0)

  val clientScreenBounds
    get() = Rectangle(bounds).apply {
      clientShift.let {
        x += it.x
        y += it.y
      }
    }

  override fun getType(): Int {
    return TYPE_RASTER_SCREEN
  }

  override fun getIDstring(): String {
    return idString
  }

  override fun getConfigurations(): Array<GraphicsConfiguration> {
    return arrayOf(defaultConfiguration)
  }

  override fun getDefaultConfiguration(): GraphicsConfiguration {
    return PGraphicsConfiguration
  }
}
