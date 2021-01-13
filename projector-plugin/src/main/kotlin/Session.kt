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
import org.jetbrains.projector.server.ProjectorServer

class Session(
  val host: String,
  val port: String,
  rwToken: String?,
  roToken: String?,
) {
  var rwToken: String?
    get() = getToken(ProjectorServer.TOKEN_ENV_NAME)
    set(value) = setToken(ProjectorServer.TOKEN_ENV_NAME, value)

  var roToken: String?
    get() = getToken(ProjectorServer.RO_TOKEN_ENV_NAME)
    set(value) = setToken(ProjectorServer.RO_TOKEN_ENV_NAME, value)

  init {
    System.setProperty(ProjectorServer.PORT_PROPERTY_NAME, port)
    this.rwToken = rwToken
    this.roToken = roToken
  }

  private fun getToken(tokenPropertyName: String): String? = System.getProperty(tokenPropertyName)
  private fun setToken(tokenPropertyName: String, token: String?) {
    if (token == null) {
      System.clearProperty(tokenPropertyName)
    } else {
      System.setProperty(tokenPropertyName, token)
    }
  }
}
