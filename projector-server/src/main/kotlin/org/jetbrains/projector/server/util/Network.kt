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

import com.intellij.util.io.toByteArray
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer

private val dockerVendor = byteArrayOf(0x02.toByte(), 0x42.toByte())

// Note: Avoid calling getLocalAddresses too often - on Windows NetworkInterface.getNetworkInterfaces()
// can take a lot of time: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=7039343
fun getLocalAddresses(keepIpv6: Boolean = false): List<InterfaceAddress> = NetworkInterface.getNetworkInterfaces()
  .asSequence()
  .filterNotNull()
  .filterNot {
    it.hardwareAddress != null && it.hardwareAddress.sliceArray(0..1).contentEquals(dockerVendor)
  } // drop docker
  .flatMap { it.interfaceAddresses?.asSequence()?.filterNotNull() ?: emptySequence() }
  .filter { keepIpv6 || it.address is Inet4Address }
  .toList()

val LOCAL_ADDRESSES = getLocalAddresses()

fun getHostsList(toHost: (ip: InetAddress) -> Host): List<Host> = LOCAL_ADDRESSES.map { toHost(it.address) }

fun ipString2Bytes(src: String): ByteArray {
  return when {
    isIp4String(src) -> ip4String2Bytes(src)
    isIp6String(src) -> ip6String2Bytes(src)
    else -> error("Invalid ip address string representation: $src")
  }
}

fun isIp4String(address: String): Boolean {
  val parts = address.split('.').filter { it.isNotEmpty() }
  return parts.size == 4 && address.all { it.isDigit() || it == '.' }
}

private fun Char.isHexDigit(): Boolean {
  val c = this.uppercaseChar()
  return c.isDigit() || c in 'A'..'Z'
}

fun isIp6String(address: String): Boolean {
  val parts = address.split(':').filter { it.isNotEmpty() }
  return parts.size == 8 && address.all { it.isHexDigit() || it == ':' }
}

private fun ip4String2Bytes(src: String) = src.split('.')
  .filter { it.isNotEmpty() }
  .map { it.toInt() }
  .map { it.toByte() }
  .toByteArray()

private fun ShortArray.toByteArray(): ByteArray {
  val bytes = ByteBuffer.allocate(this.size * 2)
  this.forEach { bytes.putShort(it) }
  return bytes.toByteArray()
}

private fun ip6String2Bytes(src: String): ByteArray {
  return src.split(':')
    .filter { it.isNotEmpty() }
    .map { it.toInt(16) }
    .map { it.toShort() }
    .toShortArray()
    .toByteArray()
}
