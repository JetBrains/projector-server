import com.intellij.openapi.util.BuildNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.internal.os.OperatingSystem
import java.io.File

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

public fun isIdeVersionAtLeast(idePath: String, minVersion: String): Boolean {
  val productInfo = getProductInfoFile(idePath)
  if (!productInfo.exists()) return false
  val jsonRoot = Json.parseToJsonElement(productInfo.readText()) as JsonObject
  val versionString = jsonRoot["buildNumber"] as JsonPrimitive
  val ideBuildNumber = BuildNumber.fromString(versionString.content)!!
  val requiredBuildNumber = BuildNumber.fromString(minVersion)!!
  return ideBuildNumber >= requiredBuildNumber
}

public fun getIdePrefix(idePath: String): String? {
  val productInfo = getProductInfoFile(idePath)
  if (!productInfo.exists()) return null
  val jsonRoot = Json.parseToJsonElement(productInfo.readText()) as JsonObject
  val launchConfigs = jsonRoot["launch"] as JsonArray
  val launchConfig = launchConfigs.first() as JsonObject
  val startScript = (launchConfig["launcherPath"] as JsonPrimitive).content
  val startScriptFile = File("$idePath/$startScript")
  val regex = Regex("-Didea.platform.prefix=(?<prefix>\\w+)")

  startScriptFile.useLines { lines ->
    lines.forEach {
      val prefix = regex.find(it)?.groups?.get("prefix")?.value
      if (prefix != null) return prefix
    }
  }

  return null
}

private fun getProductInfoFile(idePath: String): File = when {
  OperatingSystem.current().isMacOsX -> File("$idePath/MacOS/product-info.json")
  else -> File("$idePath/product-info.json")
}
