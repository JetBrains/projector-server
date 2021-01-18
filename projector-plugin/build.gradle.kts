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
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

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

tasks.withType<PatchPluginXmlTask> {
  changeNotes(
    """
    Add change notes here.<br>
    <em>most HTML tags may be used</em>
    """
  )
}
