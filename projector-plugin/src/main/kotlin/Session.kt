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
import org.jetbrains.projector.server.ProjectorServer

class Session(
  host: String,
  val port: String,
  rwToken: String?,
  roToken: String?,
  confirmConnection: Boolean,
  val autostart: Boolean
) {
  var host: String = host
    set(value) {
      field = value
      ProjectorService.host = value
    }

  var rwToken: String?
    get() = getToken(ProjectorServer.TOKEN_ENV_NAME)
    set(value) = setToken(ProjectorServer.TOKEN_ENV_NAME, value)

  var roToken: String?
    get() = getToken(ProjectorServer.RO_TOKEN_ENV_NAME)
    set(value) = setToken(ProjectorServer.RO_TOKEN_ENV_NAME, value)

  var confirmConnection: Boolean
    get() = System.getProperty(ProjectorServer.ENABLE_CONNECTION_CONFIRMATION) == "true"
    set(value) {
      System.setProperty(ProjectorServer.ENABLE_CONNECTION_CONFIRMATION, if (value) "true" else "false")
    }

  init {
    System.setProperty(ProjectorServer.PORT_PROPERTY_NAME, port)
    System.setProperty(ProjectorServer.HOST_PROPERTY_NAME, host)
    this.rwToken = rwToken
    this.roToken = roToken
    this.confirmConnection = confirmConnection
  }

  private fun getToken(tokenPropertyName: String): String? = System.getProperty(tokenPropertyName)
  private fun setToken(tokenPropertyName: String, token: String?) {
    if (token == null) {
      System.clearProperty(tokenPropertyName)
    }
    else {
      System.setProperty(tokenPropertyName, token)
    }
  }
}
