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

import org.jetbrains.projector.server.ProjectorServer
import org.jetbrains.projector.util.logging.Logger
import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

// todo: Add test with mocking of logger
class SizeAware<T : Collection<*>>(initialValue: T, private val logger: Logger) : ObservableProperty<T>(initialValue) {

  override fun getValue(thisRef: Any?, property: KProperty<*>): T {
    val value = super.getValue(thisRef, property)

    if (ProjectorServer.ENABLE_BIG_COLLECTIONS_CHECKS) {
      val size = value.size
      if (size >= ProjectorServer.BIG_COLLECTIONS_CHECKS_START_SIZE) {
        logger.error { "${property.name} is too big: $size" }
      }
    }

    return value
  }
}
