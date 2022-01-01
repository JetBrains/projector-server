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
package org.jetbrains.projector.server.util

import java.net.InetAddress
import javax.swing.JLabel
import javax.swing.JOptionPane

class ConfirmConnection private constructor(private val accessType: String)
  : JLabel(), ResolvedHostSubscriber {
  private val resolver = AsyncHostResolver()

  constructor(ip: InetAddress?, accessType: String) : this(accessType) {
    text = if (ip != null) {
      resolver.resolve(this, ip)
      getMessage(ip.hostAddress)
    }
    else {
      getMessage("unknown host")
    }
  }

  constructor(hostName: String, accessType: String) : this(accessType) {
    text = getMessage(hostName)
  }

  private fun getMessage(host: String) = "<html>Somebody from $host wants to connect with $accessType access. " +
                                         "Allow the connection?"

  override fun resolved(host: Host) {
    text = getMessage(host.toString())
  }

  companion object {
    fun confirm(ip: InetAddress?, accessType: String) = ConfirmConnection(ip, accessType).doShow()

    fun confirm(hostName: String, accessType: String) = ConfirmConnection(hostName, accessType).doShow()

    private fun ConfirmConnection.doShow() =
      JOptionPane.showConfirmDialog(null, this, "New connection", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION
  }
}
