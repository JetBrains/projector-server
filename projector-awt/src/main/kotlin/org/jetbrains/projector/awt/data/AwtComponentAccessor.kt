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
package org.jetbrains.projector.awt.data

import java.awt.Component

/**
 * A small partial reflection-based version of sun.awt.AWTAccessor which is not accessible due to java modules
 */
object AwtComponentAccessor {
  val xField = Component::class.java.getDeclaredField("x").also { it.isAccessible = true }
  val yField = Component::class.java.getDeclaredField("y").also { it.isAccessible = true }
  val widthField = Component::class.java.getDeclaredField("width").also { it.isAccessible = true }
  val heightField = Component::class.java.getDeclaredField("height").also { it.isAccessible = true }

  fun setSize(target: Component, width: Int, height: Int) {
    widthField.setInt(target, width)
    heightField.setInt(target, height)
  }

  fun setLocation(target: Component, x: Int, y: Int) {
    xField.setInt(target, x)
    yField.setInt(target, y)
  }

  fun setBounds(target: Component, x: Int, y: Int, width: Int, height: Int) {
    setLocation(target, x, y)
    setSize(target, width, height)
  }
}
