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
package org.jetbrains.projector.server.service

import org.jetbrains.projector.server.log.impl.DelegatingJvmLogger
import org.jetbrains.projector.awt.service.Logger as AwtLogger

class ProjectorLogger(tag: String) : AwtLogger {

  private val delegate = DelegatingJvmLogger(tag)

  override fun error(t: Throwable?, lazyMessage: () -> String) = delegate.error(t, lazyMessage)
  override fun info(t: Throwable?, lazyMessage: () -> String) = delegate.info(t, lazyMessage)
  override fun debug(t: Throwable?, lazyMessage: () -> String) = delegate.debug(t, lazyMessage)
}
