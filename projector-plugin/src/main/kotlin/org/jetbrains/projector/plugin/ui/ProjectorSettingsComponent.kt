/*
 * Copyright (c) 2019-2023, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import com.intellij.util.ui.SwingHelper
import org.jetbrains.projector.plugin.*
import java.awt.Desktop.getDesktop
import java.io.File
import javax.swing.*


class ProjectorSettingsComponent {
  private var autostart by ProjectorService.Companion::autostart
  private var confirmConnection by ProjectorService.Companion::confirmConnection
  private var host: String by ProjectorService.Companion::host
  private var port: String by ProjectorService.Companion::port
  private var rwToken: String by ProjectorService.Companion::rwToken
  private var roToken: String by ProjectorService.Companion::roToken
  private var secureConnection by ProjectorService.Companion::secureConnection
  var certificateSource = ProjectorService.certificateSource

  private val rwTokenEditor = TokenEditor("Password for read-write access:", ProjectorService.rwToken).apply {
    onChange = { rwToken = token }
  }

  private val roTokenEditor = TokenEditor("Password for read-only access:", ProjectorService.roToken).apply {
    onChange = { roToken = token }
  }

  private var userBtn: CellBuilder<JBRadioButton>? = null

  private val mainPanel = panel {
    titledRow("Listen on") {

      row {
        cell {
          HostsList("Host:", ProjectorService.host).apply {
            onChange = { selected?.let { host = it.address } }
            isEnabled = isConnectionSettingsEditable()
          }()

          PortEditor(ProjectorService.port).apply {
            onChange = { port = value }
            isEnabled = isConnectionSettingsEditable()
          }()
        }
      }

      row {
        checkBox("Use HTTPS", ProjectorService.secureConnection).apply {
          component.addActionListener {
            secureConnection = component.isSelected
          }

          enabled(isConnectionSettingsEditable())
        }
      }
    }

    titledRow("Passwords") {

      row {
        cell {
          rwTokenEditor.label()
          rwTokenEditor.tokenTextField()
          rwTokenEditor.refreshButton()
        }
      }

      row {
        cell {
          roTokenEditor.label().apply {
            component.preferredSize = rwTokenEditor.label.preferredSize
          }
          roTokenEditor.tokenTextField()
          roTokenEditor.refreshButton()
        }
      }


    }

    titledRow("SSL") {

      row { button("Regenerate Projector certificate") { recreateKeystoreFiles() } }

      row { button("Import user certificate") { importCertificate() } }

      row {
        link("Show Projector CA File in ${RevealFileAction.getFileManagerName()}") {
          getDesktop().open(File(getPathToPluginSSLDir()))
        }
      }
    }

    titledRow("Certificate") {

      buttonGroup(this@ProjectorSettingsComponent::certificateSource) {
        row {
          radioButton("Projector CA", CertificateSource.PROJECTOR_CA).apply {
            component.addActionListener {
              certificateSource = CertificateSource.PROJECTOR_CA
            }
          }
        }
        row {
          radioButton("User imported", CertificateSource.USER_IMPORTED).apply {
            userBtn = this
            component.addActionListener {
              certificateSource = CertificateSource.USER_IMPORTED
            }
          }.enabled(isUserKeystoreFileExist())
        }
      }
    }

    titledRow("Misc") {
      row {
        checkBox("Start Projector automatically when ${productName()} starts", autostart).apply {
          component.addActionListener {
            autostart = component.isSelected
          }
        }
      }

      row {
        checkBox("Require connection confirmation", confirmConnection).apply {
          component.addActionListener {
            confirmConnection = component.isSelected
          }
        }
      }
    }
  }


  fun getPanel(): JPanel {
    return mainPanel
  }

  private fun importCertificate() {
    val (certFile, keyFile) = twoFileChooser()

    fun isValidPath(path: String?): Boolean {
      return path != null && File(path).exists()
    }

    if (isValidPath(certFile) && isValidPath(keyFile)) {
      importUserCertificate(certFile!!, keyFile!!)
      userBtn?.enabled(isUserKeystoreFileExist())
    }
    else {
      SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(
          null,
          "You should specify valid path to certificate chain and to key file",
          "Can't import user certificate...",
          JOptionPane.ERROR_MESSAGE,
        )
      }
    }
  }
}

private class CertificateChooserDialog(project: Project) : DialogWrapper(project) {
  val certFieldWithBrowseButton = TextFieldWithBrowseButton()
  val keyFieldWithBrowseButton = TextFieldWithBrowseButton()

  private val descriptor = FileChooserDescriptor(true, false, false, false, false, false)

  init {
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = panel {
      row {
        label("Certificate file")
        SwingHelper.installFileCompletionAndBrowseDialog(ProjectManager.getInstance().defaultProject,
                                                         certFieldWithBrowseButton,
                                                         "Select Certificate Chain File",
                                                         descriptor)

        certFieldWithBrowseButton()
      }

      row {
        label("Key file")
        SwingHelper.installFileCompletionAndBrowseDialog(ProjectManager.getInstance().defaultProject,
                                                         keyFieldWithBrowseButton,
                                                         "Select Key File",
                                                         descriptor)

        keyFieldWithBrowseButton()
      }
    }

    return panel
  }

}

private fun twoFileChooser(): Pair<String?, String?> {
  val chooser = CertificateChooserDialog(ProjectManager.getInstance().defaultProject)
  chooser.pack()
  chooser.show()

  if (chooser.exitCode == DialogWrapper.OK_EXIT_CODE) {
    return Pair(chooser.certFieldWithBrowseButton.text,
                chooser.keyFieldWithBrowseButton.text)
  }

  return Pair(null, null)
}
