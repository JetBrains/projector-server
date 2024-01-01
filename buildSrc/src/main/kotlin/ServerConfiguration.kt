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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*
import java.util.*

public fun Project.applyCommonServerConfiguration(javaApplication: JavaApplication) {
  fun isJdk17Project() = name.endsWith("-jdk17")

  val launcherClassName = "org.jetbrains.projector.server.ProjectorLauncher"

  javaApplication.apply {
    mainClass.set(launcherClassName)
  }

  publishToSpace()

  version = project(":projector-server-common").version

  val projectorClientGroup: String by project
  val projectorClientVersion: String by project

  dependencies {
    api(project(":projector-server-common"))
    implementation("$projectorClientGroup:projector-server-core:$projectorClientVersion")

    if (isJdk17Project()) {
      api(project(":projector-awt-jdk17"))
    }
    else {
      api(project(":projector-awt"))
    }
  }

  tasks.named<Jar>("jar") {
    manifest {
      attributes(
        "Main-Class" to launcherClassName,
      )
    }
    duplicatesStrategy = DuplicatesStrategy.WARN
  }

  // Server running tasks
  val localProperties = Properties()
  if (rootProject.file("local.properties").canRead()) {
    localProperties.load(rootProject.file("local.properties").inputStream())
  }

  // Relay arguments
  val relayURL = localProperties["ORG_JETBRAINS_PROJECTOR_SERVER_RELAY_URL"]
  val serverId = localProperties["ORG_JETBRAINS_PROJECTOR_SERVER_RELAY_SERVER_ID"]

  var relayArgs = emptyList<String>()

  if (relayURL != null && serverId != null) {
    relayArgs = listOf("-DORG_JETBRAINS_PROJECTOR_SERVER_RELAY_URL=$relayURL", "-DORG_JETBRAINS_PROJECTOR_SERVER_RELAY_SERVER_ID=$serverId")
  }

  fun JavaExec.setupHeadlessServer() {
    jvmArgs(relayArgs)
  }

  val targetJvmVersion = tasks.withType(JavaCompile::class.java).firstOrNull()?.sourceCompatibility
  checkNotNull(targetJvmVersion) { "Module target JDK version not set" }

  fun Task.checkJavaVersion() {
    doFirst {
      check(Runtime.version().feature() >= targetJvmVersion.toInt()) {
        "Task '$name' in module '${this@applyCommonServerConfiguration.name}' requires JDK version $targetJvmVersion or newer. Current version - ${Runtime.version()}"
      }
    }
  }

  createRunServerTask(
    name = "runServer",
    isAgent = false,
    localProperties,
  ) {
    checkJavaVersion()
    jvmArgs(
      "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    )
    setupHeadlessServer()
  }

  createRunIdeaTask(
    name = "runIdeaServer",
    isAgent = false,
    localProperties,
  ) {
    checkJavaVersion()
    setupHeadlessServer()
  }
}

// TODO make possible to use kotlin gradle API
private fun DependencyHandler.api(dependencyNotation: Any): Dependency? =
  add("api", dependencyNotation)

private fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? =
  add("implementation", dependencyNotation)
