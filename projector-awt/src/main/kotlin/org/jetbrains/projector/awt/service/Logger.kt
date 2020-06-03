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
package org.jetbrains.projector.awt.service

interface Logger {

  fun error(t: Throwable? = null, lazyMessage: () -> String)
  fun info(t: Throwable? = null, lazyMessage: () -> String)
  fun debug(t: Throwable? = null, lazyMessage: () -> String)

  companion object {

    var factory: (Class<*>) -> Logger = { DefaultLogger }
  }

  private object DefaultLogger : Logger {

    private fun log(message: String) {
      println("${DefaultLogger::class.simpleName}: $message")
    }

    override fun error(t: Throwable?, lazyMessage: () -> String) = log(lazyMessage())
    override fun info(t: Throwable?, lazyMessage: () -> String) = log(lazyMessage())
    override fun debug(t: Throwable?, lazyMessage: () -> String) = log(lazyMessage())
  }
}
