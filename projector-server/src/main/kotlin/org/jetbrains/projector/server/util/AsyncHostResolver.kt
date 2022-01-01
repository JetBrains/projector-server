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

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread
import org.jetbrains.projector.server.core.util.getHostName
import java.net.InetAddress
import javax.swing.SwingUtilities

class Host(val address: String, name: String) {

  val name: String = when {
    address == "127.0.0.1" -> "localhost"
    name == address -> ""
    else -> name
  }

  constructor(ip: InetAddress, name: String? = null) : this(ip2String(ip), name ?: "resolving ...")

  override fun toString() = if (name.isEmpty()) address else "$address ( $name )"

  companion object {
    private fun ip2String(ip: InetAddress) = ip.toString().substring(1)
  }
}

interface ResolvedHostSubscriber {
  fun resolved(host: Host)
}

class AsyncHostResolver {
  class Request(val client: ResolvedHostSubscriber, val ip: InetAddress)

  private val address2Name = Collections.synchronizedMap(HashMap<InetAddress, String>())
  private val queue: MutableList<Request> = Collections.synchronizedList(ArrayList<Request>())

  fun cancelPendingRequests() = queue.clear()

  fun resolve(client: ResolvedHostSubscriber, address: String): Host {
    val addr = ipString2Bytes(address)
    val ip = InetAddress.getByAddress(null, addr)
    return resolve(client, ip)
  }

  fun resolve(client: ResolvedHostSubscriber, ip: InetAddress): Host {
    val name = getName(ip)

    if (name == null) {
      addRequest(Request(client, ip))
    }

    return Host(ip, name)
  }

  private fun getName(ip: InetAddress): String? = address2Name[ip]

  private fun addRequest(req: Request) {
    queue.add(req)
    runWorker()
  }

  private fun runWorker() {
    thread {
      while (queue.isNotEmpty()) {
        var item: Request?

        synchronized(queue) {
          item = queue.firstOrNull()
          item?.let { queue.removeAt(0) }
        }

        item?.let { req ->
          val res = getName(req.ip) ?: getHostName(req.ip)

          res?.let { name ->
            address2Name[req.ip] = name
            SwingUtilities.invokeLater { req.client.resolved(Host(req.ip, name)) }
          }
        }
      }
    }
  }
}

