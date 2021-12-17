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

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import org.jetbrains.projector.plugin.ProjectorService
import org.jetbrains.projector.plugin.getPathToPluginDir
import org.jetbrains.projector.plugin.getPathToPluginSSLDir
import org.jetbrains.projector.plugin.productName
import java.awt.Desktop.getDesktop
import java.io.File
import javax.swing.*

class ProjectorSettingsComponent {
  var autostart by ProjectorService.Companion::autostart
  var host: String by ProjectorService.Companion::host
  var port: String by ProjectorService.Companion::port
  var rwToken: String by ProjectorService.Companion::rwToken
  var roToken: String by ProjectorService.Companion::roToken
  var confirmConnection by ProjectorService.Companion::confirmConnection
  var secureConnection by ProjectorService.Companion::secureConnection

  private val hosts = HostsList("Host:", ProjectorService.host).apply {
    onChange = { selected?.let { host = it.address } }
  }

  private val portEdit = PortEditor(ProjectorService.port).apply {
    onChange = { port = this.value }
  }

  private val rwTokenEditor = TokenEditor("Password for read-write access:", ProjectorService.rwToken).apply {
    onChange = { rwToken = tokenTextField.text }
  }
  private val roTokenEditor = TokenEditor("Password for read-only  access:", ProjectorService.roToken).apply {
    onChange = { roToken = tokenTextField.text }
  }

  private val confirmConnectionCheckbox = JBCheckBox("Require connection confirmation", confirmConnection).apply {
    addActionListener {
      confirmConnection = isSelected
    }
  }

  private val secureConnectionCheckbox: JCheckBox = JCheckBox("Use HTTPS", ProjectorService.secureConnection).apply {
    addActionListener {
      secureConnection = isSelected
    }
  }

  private val autostartCheckbox = JBCheckBox("Start Projector automatically when ${productName()} starts", autostart).apply {
    addActionListener {
      autostart = isSelected
    }
  }


  private val mainPanel = panel {
    row { autostartCheckbox() }

    titledRow("Connection") {}

    row {
      hosts()
      portEdit()
    }

    row {
      rwTokenEditor.label()
      rwTokenEditor.tokenTextField()
      rwTokenEditor.refreshButton()
    }

    row {
      roTokenEditor.label()
      roTokenEditor.tokenTextField()
      roTokenEditor.refreshButton()
    }

    row { confirmConnectionCheckbox() }

    row { secureConnectionCheckbox() }

    titledRow("SSL") {}

    row { link("Show Certificate File in Files") { getDesktop().open(File(getPathToPluginSSLDir())) } }
  }

  fun getPanel(): JPanel {
    return mainPanel
  }
}
