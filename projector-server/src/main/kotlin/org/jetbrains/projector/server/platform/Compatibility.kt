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
package org.jetbrains.projector.server.platform

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.BuildNumber
import java.lang.IllegalArgumentException

fun buildAtLeast(version: String): Boolean {
  val versionToCheck = BuildNumber.fromString(version) ?: throw IllegalArgumentException("Invalid version string $version")
  val currentVersion = ApplicationInfo.getInstance().build

  return currentVersion >= versionToCheck
}

private val isColorSchemeRequired: Boolean by lazy { buildAtLeast("202.6397.94")}

@Suppress("DEPRECATION")
fun RangeHighlighter.getTextAttributesCompat(colorScheme: EditorColorsScheme): TextAttributes? {
  return if (isColorSchemeRequired) getTextAttributes(colorScheme) else textAttributes
}
