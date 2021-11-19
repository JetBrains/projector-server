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
package org.jetbrains.projector.plugin.ui

import com.intellij.openapi.ui.ComboBox
import org.jetbrains.projector.server.util.*
import java.net.InetAddress
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

class HostsList(label: String, selectedHost: String) : JPanel(), ResolvedHostSubscriber {
  private val title = JLabel(label)
  private val hosts: JComboBox<Host> = ComboBox<Host>().apply {
    val hosts = listOf(ALL_HOSTS) + getHostsList { ip: InetAddress -> AsyncHostResolver.resolve(this@HostsList, ip) }
    hosts.forEach(::addItem)
    selectedItem = hosts.find { it.address == selectedHost }
    maximumRowCount = MAX_HOSTS_ROW_COUNT
    addActionListener { onChange?.invoke() }
  }

  fun clear() = hosts.removeAllItems()

  private fun addItems(values: List<Host>) = values.forEach { hosts.addItem(it) }

  fun setItems(values: List<Host>) {
    clear()
    addItems(values)
  }

  init {
    hosts.prototypeDisplayValue = Host("255.255.255.255", "long.host.name.com")
    LinearPanelBuilder(this)
      .addNextComponent(title)
      .addNextComponent(hosts)
  }

  var onChange: (() -> Unit)? = null

  var selected
    get() = hosts.selectedItem as? Host
    set(value) {
      hosts.selectedItem = value
    }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    hosts.isEnabled = enabled
  }

  var tooltip: String?
    get() = hosts.toolTipText
    set(value) {
      hosts.toolTipText = value
    }

  override fun resolved(host: Host) {
    val oldSelection = hosts.selectedIndex
    for (i in 1 until hosts.itemCount) {
      val item = hosts.getItemAt(i)
      if (item.address == host.address) {
        hosts.removeItemAt(i)
        hosts.insertItemAt(host, i)
      }
    }

    hosts.selectedIndex = oldSelection
  }

  companion object {
    private const val MAX_HOSTS_ROW_COUNT = 15
  }
}
