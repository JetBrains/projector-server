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
package org.jetbrains.projector.server.log

import org.jetbrains.projector.server.log.impl.DelegatingJvmLogger
import org.jetbrains.projector.server.log.impl.JvmLogger

// todo: open only for mocking, need to provide an interface
open class Logger(tag: String) {

  private val implementation: JvmLogger = DelegatingJvmLogger(tag)

  open fun error(t: Throwable? = null, lazyMessage: () -> String) = implementation.error(t, lazyMessage)

  fun info(t: Throwable? = null, lazyMessage: () -> String) = implementation.info(t, lazyMessage)

  fun debug(t: Throwable? = null, lazyMessage: () -> String) = implementation.debug(t, lazyMessage)
}
