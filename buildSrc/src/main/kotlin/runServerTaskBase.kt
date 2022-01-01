/*
 * Copyright (c) 2019-2022, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import java.util.*

public fun Project.createRunServerTask(
  name: String,
  isAgent: Boolean,
  localProperties: Properties,
  configuration: JavaExec.() -> Unit,
) {

  val serverTargetClasspath = localProperties["projectorLauncher.targetClassPath"]
  val serverClassToLaunch = localProperties["projectorLauncher.classToLaunch"] as? String

  println("----------- $name config ---------------")
  println("Classpath: $serverTargetClasspath")
  println("ClassToLaunch: $serverClassToLaunch")
  println("------------------------------------------------")

  if (serverTargetClasspath != null && serverClassToLaunch != null) {
    val (classToLaunchProperty, launcherClassName) = getLaunchingSetup(isAgent)
    createRunProjectorTask(name, classToLaunchProperty, serverClassToLaunch, launcherClassName) {
      classpath(serverTargetClasspath)
      configuration()
    }
  }
}

internal data class LaunchingSetup(val classToLaunchProperty: String, val launcherClassName: String)

internal fun getLaunchingSetup(isAgent: Boolean): LaunchingSetup {
  val (classToLaunchProperty, launcherClassName) = when (isAgent) {
    true -> "org.jetbrains.projector.agent.classToLaunch" to "org.jetbrains.projector.agent.AgentLauncher"
    false -> "org.jetbrains.projector.server.classToLaunch" to "org.jetbrains.projector.server.ProjectorLauncher"
  }

  return LaunchingSetup(classToLaunchProperty, launcherClassName)
}
