/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. JetBrains designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact JetBrains, Na Hrebenech II 1718/10, Prague, 14000, Czech Republic
 * if you need additional information or have any questions.
 */
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package org.jetbrains.projector.agent

import javassist.ClassPool
import javassist.LoaderClassPath
import org.jetbrains.projector.server.core.classloader.ProjectorClassLoaderSetup
import org.jetbrains.projector.util.loading.unprotect
import org.jetbrains.projector.util.logging.Logger
import java.lang.instrument.Instrumentation
import java.lang.reflect.Method

@Suppress("unused")
public object MainAgent {

  private val logger = Logger<MainAgent>()

  @JvmStatic
  public fun agentmain(args: String?, instrumentation: Instrumentation) {
    val prjClassLoader = ProjectorClassLoaderSetup.initClassLoader(javaClass.classLoader)

    /**
     * [Starter.start]
     */
    prjClassLoader
      .loadClass("${javaClass.name}\$Starter")
      .getDeclaredMethod("start", String::class.java, Instrumentation::class.java)
      .apply(Method::unprotect)
      .invoke(null, args, instrumentation)
  }

  private object Starter {

    @JvmStatic
    fun start(args: String?, instrumentation: Instrumentation) {
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
}
