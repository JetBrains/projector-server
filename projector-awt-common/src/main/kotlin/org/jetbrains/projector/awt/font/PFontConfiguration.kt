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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt.font

import sun.awt.FontConfiguration
import sun.font.CompositeFontDescriptor
import sun.font.SunFontManager
import java.nio.charset.Charset
import java.util.*

class PFontConfiguration : FontConfiguration {

  constructor(fm: SunFontManager) : super(fm)

  constructor(fm: SunFontManager, preferLocaleFonts: Boolean, preferPropFonts: Boolean) :
    super(fm, preferLocaleFonts, preferPropFonts)

  override fun getDefaultFontCharset(fontName: String): Charset {
    return Charset.forName("UTF-8")
  }

  override fun getEncoding(awtFontName: String, characterSubsetname: String): String {
    return "default"
  }

  override fun getFaceNameFromComponentFontName(arg0: String): String? {
    return null
  }

  override fun getFallbackFamilyName(fontName: String, defaultFallback: String): String {
    return defaultFallback
  }

  override fun getFileNameFromComponentFontName(componentFontName: String): String? {
    return getFileNameFromPlatformName(componentFontName)
  }

  override fun initReorderMap() {
    reorderMap = HashMap<String, Any>()
  }

  override fun getExtraFontPath(): String? = null

  override fun get2DCompositeFontInfo(): Array<CompositeFontDescriptor> = emptyArray()
}
