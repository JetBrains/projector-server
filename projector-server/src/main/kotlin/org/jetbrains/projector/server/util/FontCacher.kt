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

package org.jetbrains.projector.server.util

import org.jetbrains.projector.common.protocol.data.FontDataHolder
import org.jetbrains.projector.common.protocol.data.TtfFontData
import org.jetbrains.projector.server.core.util.ObjectIdCacher
import org.jetbrains.projector.server.core.util.unprotect
import org.jetbrains.projector.server.service.ProjectorFontProvider
import sun.font.CompositeFont
import sun.font.FileFont
import sun.font.Font2D
import java.awt.Font
import java.io.File
import java.util.*

object FontCacher {

  private val filePathCacher = ObjectIdCacher<Short, String>(0) { (it + 1).toShort() }

  fun getId(font: Font): Short? {
    val filePath = font.getFilePath() ?: return null

    return filePathCacher.getIdBy(filePath)
  }

  fun getFontData(fontId: Short): FontDataHolder {
    val filePath = filePathCacher.getObjectBy(fontId)

    val data = File(filePath).readBytes()
    val base64 = String(Base64.getEncoder().encode(data))

    return FontDataHolder(fontId, TtfFontData(ttfBase64 = base64))
  }

  private val publicFileNameMethod = FileFont::class.java.getDeclaredMethod("getPublicFileName").apply {
    unprotect()
  }

  private fun Font2D.getFilePath(): String? {
    when (this) {
      is CompositeFont -> {
        for (i in 0 until this.numSlots) {
          val physicalFont = this.getSlotFont(i)
          return physicalFont.getFilePath()  // todo: use not only the first
        }

        return null
      }

      is FileFont -> return publicFileNameMethod.invoke(this) as String

      else -> return null
    }
  }

  private val getFont2DMethod = Font::class.java.getDeclaredMethod("getFont2D").apply {
    unprotect()
  }

  private fun Font.getFilePath(): String? {
    return ProjectorFontProvider.findFont2D(this.name, this.style, 0).getFilePath()
  }
}
