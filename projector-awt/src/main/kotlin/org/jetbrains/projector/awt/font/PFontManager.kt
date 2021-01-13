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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt.font

import org.jetbrains.projector.awt.service.FontProvider
import sun.awt.FontConfiguration
import sun.font.Font2D
import sun.font.PhysicalFont
import sun.font.SunFontManager
import java.awt.Font
import java.util.*

object PFontManager : SunFontManager() {

  override fun getAllInstalledFonts(): Array<Font> {
    return FontProvider.instance.installedFonts.toTypedArray()
  }

  override fun getInstalledFontFamilyNames(locale: Locale?): Array<String> {
    return FontProvider.instance.installedFonts.map { it.getFamily(locale) }.toTypedArray()
  }

  override fun createFontConfiguration(): FontConfiguration {
    return PFontConfiguration(this)
  }

  override fun createFontConfiguration(preferLocaleFonts: Boolean, preferPropFonts: Boolean): FontConfiguration {
    return PFontConfiguration(this, preferLocaleFonts, preferPropFonts)
  }

  override fun getDefaultPlatformFont(): Array<String> {
    return FontProvider.instance.defaultPlatformFont
  }

  override fun getFontPath(noType1Fonts: Boolean): String {
    return ""  // TODO
  }

  override fun getDefaultPhysicalFont(): PhysicalFont {
    return FontProvider.instance.defaultPhysicalFont
  }

  override fun findFont2D(name: String, style: Int, fallback: Int): Font2D {
    return FontProvider.instance.findFont2D(name, style, fallback)
  }
}
