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

package org.jetbrains.projector.server.service

import org.jetbrains.projector.awt.font.PFontManager
import org.jetbrains.projector.awt.service.FontProvider
import sun.font.Font2D
import sun.font.PhysicalFont
import java.awt.Font
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ProjectorFontProvider : FontProvider {

  private val defaultRegular by lazy { loadFont(DEFAULT_R_NAME, DEFAULT_R_PATH) }
  private val defaultRegularItalic by lazy { loadFont(DEFAULT_RI_NAME, DEFAULT_RI_PATH) }
  private val defaultBold by lazy { loadFont(DEFAULT_B_NAME, DEFAULT_B_PATH) }
  private val defaultBoldItalic by lazy { loadFont(DEFAULT_BI_NAME, DEFAULT_BI_PATH) }

  private val monoRegular by lazy { loadFont(MONO_R_NAME, MONO_R_PATH) }
  private val monoRegularItalic by lazy { loadFont(MONO_RI_NAME, MONO_RI_PATH) }
  private val monoBold by lazy { loadFont(MONO_B_NAME, MONO_B_PATH) }
  private val monoBoldItalic by lazy { loadFont(MONO_BI_NAME, MONO_BI_PATH) }

  private val allInstalledFonts by lazy {
    fun Font2D.toFont() = Font(getFamilyName(null), style, DEFAULT_SIZE)

    listOf(
      defaultRegular,
      defaultRegularItalic,
      defaultBold,
      defaultBoldItalic,
      monoRegular,
      monoRegularItalic,
      monoBold,
      monoBoldItalic
    ).map(Font2D::toFont)
  }

  override val installedFonts get() = allInstalledFonts

  override val defaultPhysicalFont: PhysicalFont get() = defaultRegular

  override val defaultPlatformFont: Array<String> get() = arrayOf(DEFAULT_FONT_NAME, DEFAULT_FONT_PATH)

  override fun findFont2D(name: String, style: Int, fallback: Int): Font2D {
    if (isMonospacedFont(name)) {
      return when (style) {
        Font.BOLD or Font.ITALIC -> monoBoldItalic

        Font.BOLD -> monoBold

        Font.ITALIC -> monoRegularItalic

        else -> monoRegular
      }
    }
    else {
      return when (style) {
        Font.BOLD or Font.ITALIC -> defaultBoldItalic

        Font.BOLD -> defaultBold

        Font.ITALIC -> defaultRegularItalic

        else -> defaultRegular
      }
    }
  }

  private fun isMonospacedFont(name: String): Boolean {
    return "mono" in name.toLowerCase() || name.toLowerCase() == "menlo"
  }

  private fun loadFont(fontName: String, fontPath: String): PhysicalFont {
    val tempFile = File.createTempFile(fontName, ".ttf").apply {
      deleteOnExit()
    }

    val link = PFontManager::class.java.getResourceAsStream(fontPath)
    Files.copy(link, tempFile.absoluteFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    return PFontManager.createFont2D(tempFile, Font.TRUETYPE_FONT, false, false, null).single() as PhysicalFont
  }

  private const val DEFAULT_R_NAME = "Default-R"
  private const val DEFAULT_R_PATH = "/fonts/Default-R.ttf"

  private const val DEFAULT_RI_NAME = "Default-RI"
  private const val DEFAULT_RI_PATH = "/fonts/Default-RI.ttf"

  private const val DEFAULT_B_NAME = "Default-B"
  private const val DEFAULT_B_PATH = "/fonts/Default-B.ttf"

  private const val DEFAULT_BI_NAME = "Default-BI"
  private const val DEFAULT_BI_PATH = "/fonts/Default-BI.ttf"


  private const val MONO_R_NAME = "Mono-R"
  private const val MONO_R_PATH = "/fonts/Mono-R.ttf"

  private const val MONO_RI_NAME = "Mono-RI"
  private const val MONO_RI_PATH = "/fonts/Mono-RI.ttf"

  private const val MONO_B_NAME = "Mono-B"
  private const val MONO_B_PATH = "/fonts/Mono-B.ttf"

  private const val MONO_BI_NAME = "Mono-BI"
  private const val MONO_BI_PATH = "/fonts/Mono-BI.ttf"

  private const val DEFAULT_FONT_NAME = DEFAULT_R_NAME
  private const val DEFAULT_FONT_PATH = DEFAULT_R_PATH

  private const val DEFAULT_SIZE = 12
}
