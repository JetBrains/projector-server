/*
 * Copyright (c) 2019-2024, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import org.jetbrains.projector.util.loading.ProjectorClassLoaderSetup
import org.jetbrains.projector.util.loading.unprotect
import java.lang.reflect.Method

abstract class ProjectorLauncherBase {

  companion object {

    /* Field is public for the case, when someone would like to launch server from their own code. */
    @Suppress("Could be private")
    const val MAIN_CLASS_PROPERTY_NAME = "org.jetbrains.projector.server.classToLaunch"

    @JvmStatic
    protected fun start(args: Array<String>, awtProvider: PAwtProvider) {

      /**
       * [ProjectorStarter.start]
       */
      getStarterClass()
        .getDeclaredMethod("start", Array<String>::class.java, PAwtProvider::class.java)
        .apply(Method::unprotect)
        .invoke(null, args, awtProvider)
    }

    @JvmStatic
    protected fun runProjectorServer(awtProvider: PAwtProvider): Boolean {

      /**
       * [ProjectorStarter.runProjectorServer]
       */
      return getStarterClass()
        .getDeclaredMethod("runProjectorServer", PAwtProvider::class.java)
        .apply(Method::unprotect)
        .invoke(null, awtProvider) as Boolean
    }

    @JvmStatic
    private fun getStarterClass(): Class<*> {
      val thisClass = ProjectorLauncherBase::class.java
      val prjClassLoader = ProjectorClassLoaderSetup.initClassLoader(thisClass.classLoader)

      return prjClassLoader.loadClass("${thisClass.packageName}.ProjectorStarter")
    }
  }

}
