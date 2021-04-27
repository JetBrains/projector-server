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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.server.service

import org.jetbrains.projector.awt.font.PFontManager
import org.jetbrains.projector.awt.service.FontProvider
import sun.font.CompositeFont
import sun.font.Font2D
import sun.font.PhysicalFont
import java.awt.Font
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ProjectorFontProvider : FontProvider {

  private val cjkRegularFile by lazy { createFontFile(CJK_R_NAME, CJK_R_PATH) }
  private val cjkRegularFont by lazy { loadPhysicalFont(cjkRegularFile) }

  private val defaultRegularFile by lazy { createFontFile(DEFAULT_R_NAME, DEFAULT_R_PATH) }
  private val defaultRegularFont by lazy { loadPhysicalFont(defaultRegularFile) }
  private val defaultRegularComposite by lazy { createCompositeFont(DEFAULT_R_NAME, "plain", defaultRegularFile) }

  private val defaultRegularItalicFile by lazy { createFontFile(DEFAULT_RI_NAME, DEFAULT_RI_PATH) }
  private val defaultRegularItalicFont by lazy { loadPhysicalFont(defaultRegularItalicFile) }
  private val defaultRegularItalicComposite by lazy { createCompositeFont(DEFAULT_RI_NAME, "italic", defaultRegularItalicFile) }

  private val defaultBoldFile by lazy { createFontFile(DEFAULT_B_NAME, DEFAULT_B_PATH) }
  private val defaultBoldFont by lazy { loadPhysicalFont(defaultBoldFile) }
  private val defaultBoldComposite by lazy { createCompositeFont(DEFAULT_B_NAME, "bold", defaultBoldFile) }

  private val defaultBoldItalicFile by lazy { createFontFile(DEFAULT_BI_NAME, DEFAULT_BI_PATH) }
  private val defaultBoldItalicFont by lazy { loadPhysicalFont(defaultBoldItalicFile) }
  private val defaultBoldItalicComposite by lazy { createCompositeFont(DEFAULT_BI_NAME, "bolditalic", defaultBoldItalicFile) }

  private val monoRegularFile by lazy { createFontFile(MONO_R_NAME, MONO_R_PATH) }
  private val monoRegularFont by lazy { loadPhysicalFont(monoRegularFile) }
  private val monoRegularComposite by lazy { createCompositeFont(MONO_R_NAME, "plain", monoRegularFile) }

  private val monoRegularItalicFile by lazy { createFontFile(MONO_RI_NAME, MONO_RI_PATH) }
  private val monoRegularItalicFont by lazy { loadPhysicalFont(monoRegularItalicFile) }
  private val monoRegularItalicComposite by lazy { createCompositeFont(MONO_RI_NAME, "italic", monoRegularItalicFile) }

  private val monoBoldFile by lazy { createFontFile(MONO_B_NAME, MONO_B_PATH) }
  private val monoBoldFont by lazy { loadPhysicalFont(monoBoldFile) }
  private val monoBoldComposite by lazy { createCompositeFont(MONO_B_NAME, "bold", monoBoldFile) }

  private val monoBoldItalicFile by lazy { createFontFile(MONO_BI_NAME, MONO_BI_PATH) }
  private val monoBoldItalicFont by lazy { loadPhysicalFont(monoBoldItalicFile) }
  private val monoBoldItalicComposite by lazy { createCompositeFont(MONO_BI_NAME, "bolditalic", monoBoldItalicFile) }

  private val allInstalledFonts by lazy {
    fun Font2D.toFont() = Font(getFamilyName(null), style, DEFAULT_SIZE)

    listOf(
      defaultRegularFont,
      defaultRegularItalicFont,
      defaultBoldFont,
      defaultBoldItalicFont,
      monoRegularFont,
      monoRegularItalicFont,
      monoBoldFont,
      monoBoldItalicFont,
    ).map(Font2D::toFont)
  }

  override val installedFonts get() = allInstalledFonts

  override val defaultPhysicalFont: PhysicalFont get() = defaultRegularFont

  override val defaultPlatformFont: Array<String> get() = arrayOf(DEFAULT_FONT_NAME, DEFAULT_FONT_PATH)

  override fun findFont2D(name: String, style: Int, fallback: Int): Font2D {
    when (name) {
      DEFAULT_R_NAME -> return defaultRegularFont
      DEFAULT_RI_NAME -> return defaultRegularItalicFont
      DEFAULT_B_NAME -> return defaultBoldFont
      DEFAULT_BI_NAME -> return defaultBoldItalicFont
      MONO_R_NAME -> return monoRegularFont
      MONO_RI_NAME -> return monoRegularItalicFont
      MONO_B_NAME -> return monoBoldFont
      MONO_BI_NAME -> return monoBoldItalicFont
      CJK_R_NAME -> return cjkRegularFont
    }

    if (isMonospacedFont(name)) {
      return when (style) {
        Font.BOLD or Font.ITALIC -> monoBoldItalicComposite

        Font.BOLD -> monoBoldComposite

        Font.ITALIC -> monoRegularItalicComposite

        else -> monoRegularComposite
      }
    }
    else {
      return when (style) {
        Font.BOLD or Font.ITALIC -> defaultBoldItalicComposite

        Font.BOLD -> defaultBoldComposite

        Font.ITALIC -> defaultRegularItalicComposite

        else -> defaultRegularComposite
      }
    }
  }

  private fun isMonospacedFont(name: String): Boolean {
    return "mono" in name.toLowerCase() || name.toLowerCase() == "menlo"
  }

  private fun createFontFile(fontName: String, fontPath: String): File {
    val tempFile = File.createTempFile(fontName, ".ttf").apply {
      deleteOnExit()
    }

    val link = PFontManager::class.java.getResourceAsStream(fontPath)
    Files.copy(link, tempFile.absoluteFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    return tempFile
  }

  private fun loadPhysicalFont(tempFile: File): PhysicalFont {
    return PFontManager.createFont2D(tempFile, Font.TRUETYPE_FONT, false, false, null).single() as PhysicalFont
  }

  private fun createCompositeFont(fontName: String, style: String, tempFile: File): CompositeFont {
    return CompositeFont(
      "$fontName.$style",
      arrayOf(tempFile.absolutePath, cjkRegularFile.absolutePath),
      arrayOf(fontName, CJK_R_NAME),
      2,
      null,
      null,
      false,
      PFontManager,
    )
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


  private const val CJK_R_NAME = "CJK-R"
  private const val CJK_R_PATH = "/fonts/CJK-R.otf"

  private const val DEFAULT_FONT_NAME = DEFAULT_R_NAME
  private const val DEFAULT_FONT_PATH = DEFAULT_R_PATH

  private const val DEFAULT_SIZE = 12
}
