/*
 * Copyright (c) 2019-2023, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import org.jetbrains.projector.server.core.ij.log.DelegatingJvmLogger
import org.jetbrains.projector.server.service.ProjectorFontProvider
import org.jetbrains.projector.util.loading.UseProjectorLoader
import org.jetbrains.projector.util.logging.Logger
import java.awt.Toolkit
import java.lang.reflect.Method
import kotlin.system.exitProcess

@UseProjectorLoader
open class ProjectorStarter {

  companion object {

    @JvmStatic
    fun start(args: Array<String>, awtProvider: PAwtProvider) {
      val canonicalMainClassName = requireNotNull(System.getProperty(ProjectorLauncherBase.MAIN_CLASS_PROPERTY_NAME)) {
        "System property `${ProjectorLauncherBase.MAIN_CLASS_PROPERTY_NAME}` isn't assigned, so can't understand which class to launch"
      }

      val mainMethod = getMainMethodOf(canonicalMainClassName)

      if (runProjectorServer(awtProvider)) {
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

    private fun setupSingletons(projectorToolkit: Toolkit) {
      setupToolkit(projectorToolkit)
      setupFontManager()
      setupRepaintManager()
    }

    private fun initializeHeadlessGeneral() {
      setupSystemProperties()
      ProjectorFontProvider.isAgent = false
    }

    private fun initializeHeadlessFull(projectorToolkit: Toolkit) {
      setupSingletons(projectorToolkit)
    }

    @JvmStatic
    @JvmOverloads
    fun runProjectorServer(awtProvider: PAwtProvider, loggerFactory: (tag: String) -> Logger = ::DelegatingJvmLogger): Boolean {
      System.setProperty(ProjectorServer.ENABLE_PROPERTY_NAME, true.toString())

      assert(ProjectorServer.isEnabled) { "Can't start the ${ProjectorServer::class.simpleName} because it's disabled..." }

      // Initializing toolkit before awt transformer results in caching of headless property (= true)
      // and call to system graphics environment initialization, so we need firstly to set up java.awt.headless=false,
      // then set our graphics environment (via transformer), and only then to initialize toolkit
      val server = ProjectorServer.startServer(
        isAgent = false, loggerFactory,
        { initializeHeadlessGeneral() }, { initializeHeadlessFull(awtProvider.createToolkit()) },
      )

      Runtime.getRuntime().addShutdownHook(object : Thread() {

        override fun run() {
          server.stop()
        }
      })

      return server.wasStarted
    }

  }
}
