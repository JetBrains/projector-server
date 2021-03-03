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

import com.intellij.ui.table.JBTable
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.DefaultTableModel


class ConnectionPanel : JPanel() {
  private val title = JLabel("Current connections")
  private val disconnectButton = JButton("Disconnect").apply {
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
  private val updateButton = JButton("Update").apply {
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

  private fun update() {
    clientTable.model = DefaultTableModel(ProjectorService.getClientList(), columnNames)
    if (clientTable.model.rowCount > 0) {
      clientTable.setRowSelectionInterval(0, 0)
      disconnectButton.isEnabled = true
      disconnectAllButton.isEnabled = true
    }
    else {
      disconnectButton.isEnabled = false
      disconnectAllButton.isEnabled = false
    }
  }
}
