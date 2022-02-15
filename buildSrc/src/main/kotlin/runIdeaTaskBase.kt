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

public fun Project.createRunIdeaTask(
  name: String,
  isAgent: Boolean,
  localProperties: Properties,
  configuration: JavaExec.() -> Unit,
) {

  val ideaPath = localProperties["projectorLauncher.ideaPath"] as? String
  println("----------- $name config ---------------")
  println("Idea path: $ideaPath")
  println("------------------------------------------------")
  if (ideaPath == null) return

  val ideaLib = "$ideaPath/lib"
  val ideaClassPath = "$ideaLib/bootstrap.jar:$ideaLib/extensions.jar:$ideaLib/util.jar:$ideaLib/jdom.jar:$ideaLib/log4j.jar:$ideaLib/trove4j.jar"
  val jdkHome = System.getProperty("java.home")

  println("JDK home dir: $jdkHome")

  val prefix = getIdePrefix(ideaPath)
  val ideaPathsSelector = "Projector${prefix ?: "Idea"}"

  val (classToLaunchProperty, launcherClassName) = getLaunchingSetup(isAgent)

  createRunProjectorTask(name, classToLaunchProperty, "com.intellij.idea.Main", launcherClassName) {
    classpath(ideaClassPath, "$jdkHome/../lib/tools.jar")

    jvmArgs(
      "-Didea.paths.selector=$ideaPathsSelector",
      "-Didea.jre.check=true",
      "-Didea.is.internal=true",
      "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
      "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    )

    if (isIdeVersionAtLeast(ideaPath, "212")) { // appeared in 211, became default in 212, mandatory in 221
      jvmArgs("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
    }

    if (prefix != null) {
      jvmArgs("-Didea.platform.prefix=$prefix") // This is required for IDE to determine proper file locations
    }

    configuration()
  }
}
