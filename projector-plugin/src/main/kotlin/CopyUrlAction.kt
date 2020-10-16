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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.Inet4Address
import java.net.NetworkInterface

class CopyUrlAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val dockerVendor = byteArrayOf(0x02.toByte(), 0x42.toByte())

    val ips = NetworkInterface.getNetworkInterfaces()
      .asSequence()
      .filterNotNull()
      .filterNot { it.isLoopback }
      .filterNot {
        it.hardwareAddress != null
        &&
        it.hardwareAddress.sliceArray(0..1).contentEquals(dockerVendor)
      }
      .flatMap { it.interfaceAddresses?.asSequence()?.filterNotNull() ?: emptySequence() }
      .mapNotNull { (it.address as? Inet4Address)?.hostAddress }
      .toList()

    val ipsString = if (ips.size == 1) {
      ips.single()
    }
    else {
      ips.joinToString(",", prefix = "[", postfix = "]")
    }

    val connectionURL = "http://$ipsString:8887"
    Toolkit
      .getDefaultToolkit()
      .systemClipboard
      .setContents(StringSelection(connectionURL), null)
  }

  override fun update(e: AnActionEvent) {
    val state = ProjectorService.enabled == EnabledState.HAS_VM_OPTIONS_AND_ENABLED
    e.presentation.isEnabled = state
    e.presentation.isVisible = state
  }
}
