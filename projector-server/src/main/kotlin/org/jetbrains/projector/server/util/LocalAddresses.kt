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
package org.jetbrains.projector.server.util

import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface

private val dockerVendor = byteArrayOf(0x02.toByte(), 0x42.toByte())

// Note: Avoid calling getLocalAddresses too often - on Windows NetworkInterface.getNetworkInterfaces()
// can take a lot of time: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=7039343
fun getLocalAddresses() : List<InterfaceAddress> = NetworkInterface.getNetworkInterfaces()
  .asSequence()
  .filterNotNull()
  .filterNot {
    it.hardwareAddress != null && it.hardwareAddress.sliceArray(0..1).contentEquals(dockerVendor)
  } // drop docker
  .flatMap { it.interfaceAddresses?.asSequence()?.filterNotNull() ?: emptySequence() }
  .filterNot { it.address is Inet6Address } // drop IP v 6
  .toList()
