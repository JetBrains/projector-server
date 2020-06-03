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
package org.jetbrains.projector.server.log.impl

// todo: check for IDEA Logger settings and suppress disabled log levels
internal class IdeaJvmLogger(ideaClassLoader: ClassLoader, tag: String) : JvmLogger {

  private val loggerClass = Class.forName("com.intellij.openapi.diagnostic.Logger", false, ideaClassLoader)

  private val logger = loggerClass
    .getDeclaredMethod("getInstance", String::class.java)
    .invoke(null, tag)

  private val errorMethod = loggerClass
    .getDeclaredMethod("error", String::class.java, Throwable::class.java)

  private val infoMethod = loggerClass
    .getDeclaredMethod("info", String::class.java, Throwable::class.java)

  private val debugMethod = loggerClass
    .getDeclaredMethod("debug", String::class.java, Throwable::class.java)

  override fun error(t: Throwable?, lazyMessage: () -> String) {
    errorMethod.invoke(logger, lazyMessage(), t)
  }

  override fun info(t: Throwable?, lazyMessage: () -> String) {
    infoMethod.invoke(logger, lazyMessage(), t)
  }

  override fun debug(t: Throwable?, lazyMessage: () -> String) {
    debugMethod.invoke(logger, lazyMessage(), t)
  }
}
