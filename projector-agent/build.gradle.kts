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

import java.util.*

plugins {
  kotlin("jvm")
  `maven-publish`
}

publishing {
  publishOnSpace(project)
}

val agentClassName = "org.jetbrains.projector.agent.MainAgent"
val launcherClassName = "org.jetbrains.projector.agent.AgentLauncher"
version = project(":projector-plugin").version

kotlin {
  explicitApi()
}

val projectorClientGroup: String by project
val projectorClientVersion: String by project
val javassistVersion: String by project

dependencies {
  implementation("$projectorClientGroup:projector-common:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-server-core:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-util-loading:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-util-logging:$projectorClientVersion")
  api(project(":projector-awt"))
  api(project(":projector-server"))
  implementation("org.javassist:javassist:$javassistVersion")
}

// todo: undestand why `tasks.jar` doesn't work when executing runWithAgent and runIdeaWithAgent:
//       `Project#beforeEvaluate(Action) on project ':projector-client' cannot be executed in the current context.`
tasks.withType<Jar> {
  manifest {
    attributes(
      "Can-Redefine-Classes" to true,
      "Can-Retransform-Classes" to true,
      "Agent-Class" to agentClassName,
      "Main-Class" to launcherClassName,
    )
  }

  exclude("META-INF/versions/9/module-info.class")
  duplicatesStrategy = DuplicatesStrategy.WARN

  from(inline(configurations.runtimeClasspath)) // todo: remove
}

val localProperties = Properties()
if (rootProject.file("local.properties").canRead()) {
  localProperties.load(rootProject.file("local.properties").inputStream())
}

val serverTargetClasspath = localProperties["projectorLauncher.targetClassPath"]
val serverClassToLaunch = localProperties["projectorLauncher.classToLaunch"]

// Server running tasks
println("----------- Agent launch config ---------------")
println("Classpath: $serverTargetClasspath")
println("ClassToLaunch: $serverClassToLaunch")
println("------------------------------------------------")
if (serverTargetClasspath != null && serverClassToLaunch != null) {
  task<JavaExec>("runWithAgent") {
    group = "projector"
    mainClass.set(launcherClassName)
    classpath(sourceSets.main.get().runtimeClasspath, tasks.jar, serverTargetClasspath)
    jvmArgs(
      "-Djdk.attach.allowAttachSelf=true",
      "-Dswing.bufferPerWindow=false",
      "-Dorg.jetbrains.projector.agent.path=${project.file("build/libs/${project.name}-${project.version}.jar")}",
      "-Dorg.jetbrains.projector.agent.classToLaunch=$serverClassToLaunch",
    )
    dependsOn(tasks.jar)
  }
}

val ideaPath = localProperties["projectorLauncher.ideaPath"]
println("----------- Idea launch config ---------------")
println("Idea path: $ideaPath")
println("------------------------------------------------")
if (ideaPath != null) {
  val ideaLib = "$ideaPath/lib"
  val ideaClassPath = "$ideaLib/bootstrap.jar:$ideaLib/extensions.jar:$ideaLib/util.jar:$ideaLib/jdom.jar:$ideaLib/log4j.jar:$ideaLib/trove4j.jar:$ideaLib/jna.jar:$ideaPath/jbr/lib/tools.jar"
  val jdkHome = System.getProperty("java.home")

  println(jdkHome)

  task<JavaExec>("runIdeaWithAgent") {
    group = "projector"
    mainClass.set(launcherClassName)
    classpath(sourceSets.main.get().runtimeClasspath, tasks.jar, ideaClassPath, "$jdkHome/../lib/tools.jar")
    jvmArgs(
      "-Djdk.attach.allowAttachSelf=true",
      "-Dswing.bufferPerWindow=false",
      "-Dorg.jetbrains.projector.agent.path=${project.file("build/libs/${project.name}-${project.version}.jar")}",
      "-Dorg.jetbrains.projector.agent.classToLaunch=com.intellij.idea.Main",
      "-Didea.paths.selector=ProjectorIntelliJIdea2019.3",
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
  }
}
