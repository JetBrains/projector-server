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
package org.jetbrains.projector.agent

import javassist.ClassPool
import org.jetbrains.projector.server.log.Logger
import java.lang.instrument.Instrumentation
import java.util.jar.JarFile

object MainAgent {
  private val logger = Logger(MainAgent::class.simpleName!!)

  @JvmStatic
  fun premain(args: String?, instrumentation: Instrumentation) {
    logger.info { "Projector agent working..." }

    // Override swing property before swing initialized. Need for MacOS.
    System.setProperty("swing.bufferPerWindow", false.toString())

    // Make DrawHandler class visible for System classloader
    instrumentation.appendToSystemClassLoaderSearch(JarFile(args))

    // Make DrawHandler class visible for Javassist
    ClassPool.getDefault().insertClassPath(args)

    instrumentation.addTransformer(GraphicsTransformer(), true)
  }
}
