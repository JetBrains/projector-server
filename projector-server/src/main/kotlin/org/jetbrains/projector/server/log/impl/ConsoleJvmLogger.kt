/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2020 JetBrains s.r.o.
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
package org.jetbrains.projector.server.log.impl

import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

internal class ConsoleJvmLogger(private val tag: String) : JvmLogger {

  override fun error(t: Throwable?, lazyMessage: () -> String) {
    System.err.println("[ERROR] :: $tag :: ${lazyMessage()}${t.suffixForLog}")
  }

  override fun info(t: Throwable?, lazyMessage: () -> String) {
    println("[INFO] :: $tag :: ${lazyMessage()}${t.suffixForLog}")
  }

  override fun debug(t: Throwable?, lazyMessage: () -> String) {
    println("[DEBUG] :: $tag :: ${lazyMessage()}${t.suffixForLog}")
  }

  companion object {

    private val Throwable?.suffixForLog: String
      get() = when (this) {
        null -> ""

        else -> " :: $stackTraceString"
      }

    private val Throwable.stackTraceString: String
      get() {
        val str = StringWriter()
        val writer = PrintWriter(str)

        try {
          this.printStackTrace(writer)
          return str.buffer.toString()
        }
        finally {
          try {
            str.close()
            writer.close()
          }
          catch (e: IOException) {
          }
        }
      }
  }
}
