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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package org.jetbrains.projector.agent

import javassist.ClassPool
import javassist.LoaderClassPath
import org.jetbrains.projector.util.logging.Logger
import java.lang.instrument.Instrumentation

public object MainAgent {

  private val logger = Logger<MainAgent>()

  @JvmStatic
  public fun agentmain(args: String?, instrumentation: Instrumentation) {
    logger.info { "agentmain start, args=$args" }

    val threads = Thread.getAllStackTraces().keys
    val classLoaders = threads.mapNotNull { it.contextClassLoader }.toSet()
    logger.info { "Found classloaders, appending to Javassist: ${classLoaders.joinToString()}" }
    classLoaders.forEach { ClassPool.getDefault().appendClassPath(LoaderClassPath(it)) }

    // Override swing property before swing initialized. Need for MacOS.
    //System.setProperty("swing.bufferPerWindow", false.toString())  // todo: this doesn't work because Swing is initialized already

    // Make DrawHandler class visible for System classloader
    //instrumentation.appendToSystemClassLoaderSearch(JarFile(args)) // todo: seems not needed (maybe after appending all classloaders)

    instrumentation.addTransformer(GraphicsTransformer(), true)

    instrumentation.retransformClasses(
      sun.java2d.SunGraphics2D::class.java,
      sun.awt.image.SunVolatileImage::class.java,
      java.awt.image.BufferedImage::class.java,
      java.awt.Component::class.java,
      javax.swing.JComponent::class.java,
      //Class.forName("com.intellij.ui.BalloonImpl\$MyComponent"),  // todo
    )

    logger.info { "agentmain finish" }
  }
}
