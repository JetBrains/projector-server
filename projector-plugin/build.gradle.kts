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
import java.io.File
import java.nio.file.Paths

plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij")
}


val projectorClientVersion: String by project

dependencies {
  implementation("com.github.JetBrains.projector-client:projector-server-core:$projectorClientVersion")
  implementation(project(":projector-agent"))
}

intellij {
  version = "2019.3"
  updateSinceUntilBuild = false
}

(tasks["runIde"] as JavaExec).apply {
  jvmArgs = jvmArgs.orEmpty() + listOf("-Djdk.attach.allowAttachSelf=true", "-Dswing.bufferPerWindow=false")
}

abstract class GenerateVersionsFile: DefaultTask() {
  private val filePath = "src/main/resources/META-INF/pluginVersions.txt"
  @get:Input
  abstract val agentVersion: Property<String>

  @TaskAction
  fun generateVersions() {
    val fullPath = Paths.get(project.buildFile.parent, filePath)
    val f = fullPath.toFile()
    f.printWriter().use {
      it.println(getContent())
    }
  }

  private fun getContent(): String {
    val versions = listOf("agentVersion=${agentVersion.get()}")
    return versions.joinToString("\n")
  }
}

val versions = task<GenerateVersionsFile>("versions") {
  agentVersion.set(project(":projector-agent").version.toString())
}

tasks.processResources {
  dependsOn(versions)
}
