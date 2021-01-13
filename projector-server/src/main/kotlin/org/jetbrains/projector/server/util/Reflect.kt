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
package org.jetbrains.projector.server.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

fun Field.unprotect() {
  isAccessible = true

  Field::class.java.getDeclaredField("modifiers").apply {
    isAccessible = true
    setInt(this@unprotect, this@unprotect.modifiers and Modifier.FINAL.inv())
  }
}

fun Method.unprotect() {
  isAccessible = true
}
