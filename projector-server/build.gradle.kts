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

import java.net.URL
import java.util.*
import java.util.zip.ZipFile

plugins {
  kotlin("jvm")
  application
  `maven-publish`
}

val launcherClassName = "org.jetbrains.projector.server.ProjectorLauncher"

application {
  mainClass.set(launcherClassName)
}

publishToSpace()

configurations.all {
  // disable caching of -SNAPSHOT dependencies
  resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

val projectorClientGroup: String by project
val projectorClientVersion: String by project
val mockitoKotlinVersion: String by project
val kotlinVersion: String by project
val intellijPlatformVersion: String by project

dependencies {
  implementation("$projectorClientGroup:projector-common:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-server-core:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-util-loading:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-util-logging:$projectorClientVersion")
  api(project(":projector-awt"))

  compileOnly("com.jetbrains.intellij.platform:code-style:$intellijPlatformVersion")
  compileOnly("com.jetbrains.intellij.platform:core-ui:$intellijPlatformVersion")
  compileOnly("com.jetbrains.intellij.platform:ide-impl:$intellijPlatformVersion")

  testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
  testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
  testImplementation("com.jetbrains.intellij.platform:core:$intellijPlatformVersion")
}

tasks.jar {
  manifest {
    attributes(
      "Main-Class" to application.mainClass.get(),
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

createRunServerTask(
  name = "runServer",
  isAgent = false,
  localProperties,
) {
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
  setupHeadlessServer()
}

fun downloadFontsInZip(
  name: String,
  zipUrl: String,
  originalToDest: Map<String, String>,
) {
  // todo: make this function create a task like a delegate
  val fontsPath = "src/main/resources/fonts"
  val requiredFonts = originalToDest.values.map { "$fontsPath/$it" }

  println("Checking $name fonts: $requiredFonts")

  val haveAll = requiredFonts.all { project.file(it).exists() }

  if (haveAll) {
    println("$name fonts already exist, skipping download.")
  }
  else {
    println("Some $name fonts are missing, downloading... If some fonts exist, they will be overwritten.")

    project.file(fontsPath).mkdirs()

    val tempFile = File.createTempFile("${name}-fonts", "zip")
    URL(zipUrl).openStream().copyTo(tempFile.outputStream())

    val zipFile = ZipFile(tempFile)

    originalToDest.forEach { (srcPath, dest) ->
      val destFile = project.file("$fontsPath/$dest")

      destFile.delete()
      destFile.createNewFile()

      zipFile.getInputStream(zipFile.getEntry(srcPath)).copyTo(destFile.outputStream())
    }

    tempFile.delete()

    println("Download complete")
  }
}

val downloadCjkFonts by tasks.creating<Task> {
  doLast {
    downloadFontsInZip(
      "CJK",
      "https://noto-website-2.storage.googleapis.com/pkgs/NotoSansCJKjp-hinted.zip",
      mapOf(
        "NotoSansCJKjp-Regular.otf" to "CJK-R.otf",
      ),
    )
  }
}

val downloadDefaultFonts by tasks.creating<Task> {
  doLast {
    downloadFontsInZip(
      "default",
      "https://noto-website-2.storage.googleapis.com/pkgs/NotoSans-hinted.zip",
      mapOf(
        "NotoSans-Regular.ttf" to "Default-R.ttf",
        "NotoSans-Italic.ttf" to "Default-RI.ttf",
        "NotoSans-Bold.ttf" to "Default-B.ttf",
        "NotoSans-BoldItalic.ttf" to "Default-BI.ttf",
      ),
    )
  }
}

val jetbrainsMonoVersion: String by project

val downloadMonoFonts by tasks.creating<Task> {
  doLast {
    println("jetbrainsMonoVersion: $jetbrainsMonoVersion")
    downloadFontsInZip(
      "mono",
      "https://download.jetbrains.com/fonts/JetBrainsMono-$jetbrainsMonoVersion.zip",
      mapOf(
        "fonts/ttf/JetBrainsMono-Regular.ttf" to "Mono-R.ttf",
        "fonts/ttf/JetBrainsMono-Italic.ttf" to "Mono-RI.ttf",
        "fonts/ttf/JetBrainsMono-Bold.ttf" to "Mono-B.ttf",
        "fonts/ttf/JetBrainsMono-BoldItalic.ttf" to "Mono-BI.ttf",
      ),
    )
  }
}

val downloadFonts by tasks.creating<Task> {
  dependsOn(downloadCjkFonts, downloadDefaultFonts, downloadMonoFonts)
}

// Modify existing task which puts resources to the target dir:
tasks.processResources {
  dependsOn(downloadFonts)
}
