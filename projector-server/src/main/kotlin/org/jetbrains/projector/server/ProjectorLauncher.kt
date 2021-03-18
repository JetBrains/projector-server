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
package org.jetbrains.projector.server

import java.lang.reflect.Method
import kotlin.system.exitProcess

object ProjectorLauncher {

  /* Field is public for the case, when someone would like to launch server from their own code. */
  @Suppress("Could be private")
  const val MAIN_CLASS_PROPERTY_NAME = "org.jetbrains.projector.server.classToLaunch"

  @JvmStatic
  fun main(args: Array<String>) {
    val canonicalMainClassName = requireNotNull(System.getProperty(MAIN_CLASS_PROPERTY_NAME)) {
      "System property `$MAIN_CLASS_PROPERTY_NAME` isn't assigned, so can't understand which class to launch"
    }

    val mainMethod = getMainMethodOf(canonicalMainClassName)

    if (runProjectorServer()) {
      mainMethod.invoke(null, args)
    }
    else {
      exitProcess(1)
    }
  }

  private fun getMainMethodOf(canonicalClassName: String): Method {
    val mainClass = Class.forName(canonicalClassName)
    return mainClass.getMethod("main", Array<String>::class.java)
  }

  private fun setupSingletons() {
    setupGraphicsEnvironment()
    setupToolkit()
    setupFontManager()
    setupRepaintManager()
  }

  private fun initalizeHeadless() {
    setupSystemProperties()
    setupSingletons()
  }

  private fun runProjectorServer(): Boolean {
    System.setProperty(ProjectorServer.ENABLE_PROPERTY_NAME, true.toString())

    assert(ProjectorServer.isEnabled) { "Can't start the ${ProjectorServer::class.simpleName} because it's disabled..." }

    val server = ProjectorServer.startServer(isAgent = false) { initalizeHeadless() }

    Runtime.getRuntime().addShutdownHook(object : Thread() {

      override fun run() {
        server.stop()
      }
    })

    return server.wasStarted
  }
}
