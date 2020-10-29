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
import org.jetbrains.projector.server.ProjectorServer
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

object Utils {

  fun copyToClipboard(string: String) {
    Toolkit
      .getDefaultToolkit()
      .systemClipboard
      .setContents(StringSelection(string), null)
  }

  fun getPort(): String {
    val port = System.getProperty(ProjectorServer.PORT_PROPERTY_NAME)?.toIntOrNull() ?: ProjectorServer.DEFAULT_PORT
    return port.toString()
  }

  fun getUrl(host: String, port: String, token: String?): String {
    return "http://${host}:${port}" + if (token.isNullOrEmpty()) "" else "/index.html?token=${token}"
  }
}