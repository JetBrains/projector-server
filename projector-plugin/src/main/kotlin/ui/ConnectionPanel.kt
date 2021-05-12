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
package ui/*
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

import ProjectorService
import com.intellij.ui.table.JBTable
import org.jetbrains.projector.server.util.AsyncHostResolver
import org.jetbrains.projector.server.util.Host
import org.jetbrains.projector.server.util.ResolvedHostSubscriber
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.DefaultTableModel


class ConnectionPanel(private val resolver: AsyncHostResolver) : JPanel(), ResolvedHostSubscriber {
  private val title = JLabel("Current connections:")
  private val disconnectButton = JButton("Disconnect Selected").apply {
    addActionListener {
      val ip = clientTable.model.getValueAt(clientTable.selectedRow, 0).toString()
      ProjectorService.disconnectByIp(ip)
      update()
    }
  }
  private val disconnectAllButton = JButton("Disconnect All").apply {
    addActionListener {
      ProjectorService.disconnectAll()
      update()
    }
  }
  private val updateButton = JButton("Update List").apply {
    addActionListener {
      update()
    }
  }
  private val columnNames = arrayOf("Address", "Host Name")
  private val clientTable = JBTable().apply {
    preferredScrollableViewportSize = Dimension(100, 100)
    setDefaultEditor(Any::class.java, null)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  }

  init {
    isVisible = ProjectorService.isSessionRunning
    if (ProjectorService.isSessionRunning) {
      val buttonPanel = JPanel()
      LinearPanelBuilder(buttonPanel)
        .addNextComponent(updateButton).addNextComponent(disconnectButton).addNextComponent(disconnectAllButton)

      LinearPanelBuilder(this).addNextComponent(title, topGap = 5, bottomGap = 5)
        .startNextLine().addNextComponent(JScrollPane(clientTable))
        .startNextLine().addNextComponent(buttonPanel, topGap = 5)

      update()
    }
  }

  private fun resolveClients() {
    val model = clientTable.model

    for (i in 0 until model.rowCount) {
      val addr = model.getValueAt(i, 0).toString()
      val host = resolver.resolve(this, addr)
      model.setValueAt(host.name, i, 1)
    }
  }

  private fun update() {
    clientTable.model = DefaultTableModel(ProjectorService.getClientList(), columnNames)
    if (clientTable.model.rowCount > 0) {
      clientTable.setRowSelectionInterval(0, 0)
      disconnectButton.isEnabled = true
      disconnectAllButton.isEnabled = true
      resolveClients()
    }
    else {
      disconnectButton.isEnabled = false
      disconnectAllButton.isEnabled = false
    }
  }

  override fun resolved(host: Host) {
    for (i in 0 until clientTable.model.rowCount) {
      val addr = clientTable.model.getValueAt(i, 0)

      if (addr == host.address) {
        clientTable.model.setValueAt(host.name, i, 1)
      }
    }
  }
}
