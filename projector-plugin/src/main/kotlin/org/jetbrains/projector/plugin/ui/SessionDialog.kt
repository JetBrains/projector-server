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

package org.jetbrains.projector.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import org.jetbrains.projector.plugin.ProjectorService
import org.jetbrains.projector.plugin.ProjectorSettingsConfigurable
import org.jetbrains.projector.plugin.isConnectionSettingsEditable
import org.jetbrains.projector.server.util.*
import org.jetbrains.projector.server.util.getHostsList
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

class SessionDialog(project: Project?) : DialogWrapper(project) {
  private val description = JLabel()
  private val myHostsList: HostsList = HostsList("Host:", ProjectorService.host)
  private val urlHostsList = HostsList("URL: ", "")
  private val portEditor = PortEditor(ProjectorService.port)
  private val secureConnection: JCheckBox = JCheckBox("Use HTTPS", ProjectorService.secureConnection).apply {
    addActionListener {
      updateInvitationLinks()
    }
  }

  private val useNamesInURLs: JCheckBox = JCheckBox("Use names in URLs", secureConnection.isSelected).apply {
    addActionListener {
      updateInvitationLinks()
    }
  }

  private val rwInvitationLink = InvitationLink("Read/Write Link:")
  private val roInvitationLink = InvitationLink("Read Only Link:")

  val listenAddress: String get() = myHostsList.selected?.address.orEmpty()
  val listenPort: String get() = portEditor.value
  private val urlAddress: String
    get() {
      if (useNamesInURLs.isSelected) {
        return urlHostsList.selected?.name.orEmpty()
      }

      return urlHostsList.selected?.address.orEmpty()
    }

  val useSecureConnection: Boolean get() = secureConnection.isSelected

  init {

    if (isConnectionSettingsEditable()) {
      title = "Start Remote Access to IDE"
      description.text = "Configure remote access to IDE"
      myOKAction.putValue(Action.NAME, "Start")
    }
    else {
      title = "Current Session"
      description.text = "The current session has already started"
      myOKAction.putValue(Action.NAME, "Save")

      portEditor.isEnabled = false
      myHostsList.isEnabled = false
      secureConnection.isEnabled = false
    }

    myHostsList.onChange = ::updateURLList
    urlHostsList.onChange = ::updateInvitationLinks
    portEditor.onChange = ::updateInvitationLinks

    updateURLList()
    updateInvitationLinks()
    isResizable = false
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = panel {
      row { description() }
      titledRow("Listen on") {
        row {
          cell {
            myHostsList()
            portEditor()
          }
        }
        row { secureConnection() }
      }

      titledRow("Invitation Links:") {
        row { urlHostsList() }
        row { useNamesInURLs() }
        row {
          cell {
            rwInvitationLink.label()
            rwInvitationLink.link()
            rwInvitationLink.copyButton()
          }
        }

        row {
          cell {
            roInvitationLink.label().apply {
              component.preferredSize = rwInvitationLink.label.preferredSize
            }
            roInvitationLink.link()
            roInvitationLink.copyButton()
          }
        }
      }

      row {
        val cpn = ConnectionPanel()
        cpn()
      }

      row {
        link("More settings") { openSettings() }
      }
    }

    return panel
  }

  private fun openSettings() {
    val project = ProjectManager.getInstance().defaultProject
    ShowSettingsUtil.getInstance().editConfigurable(project, ProjectorSettingsConfigurable())

    if (listenAddress != ProjectorService.host) {
      myHostsList.selectByAddress(ProjectorService.host)
      updateURLList()
    }

    portEditor.value = ProjectorService.port
    secureConnection.isSelected = ProjectorService.secureConnection

    updateInvitationLinks()
  }

  private fun updateInvitationLinks() {
    val scheme = if (useSecureConnection) "https" else "http"
    rwInvitationLink.update(scheme, urlAddress, listenPort, ProjectorService.rwToken)
    roInvitationLink.update(scheme, urlAddress, listenPort, ProjectorService.roToken)
  }

  fun cancelResolverRequests() {
    AsyncHostResolver.cancelPendingRequests()
  }

  private fun updateURLList() {
    val host = myHostsList.selected

    when {
      host == ALL_HOSTS -> {
        urlHostsList.tooltip = null
        val oldValue = urlHostsList.selected
        val hostList = getHostsList { ip -> AsyncHostResolver.resolve(urlHostsList, ip) }
        urlHostsList.setItems(hostList)
        urlHostsList.selected = oldValue
        urlHostsList.isEnabled = hostList.size > 1
      }
      host != null -> {
        urlHostsList.tooltip = "nothing to select from"
        urlHostsList.setItems(listOf(host))
        urlHostsList.selected = host
        urlHostsList.isEnabled = false
        AsyncHostResolver.resolve(myHostsList, host.address)
        AsyncHostResolver.resolve(urlHostsList, host.address)
      }
      else -> {
        urlHostsList.tooltip = null
        urlHostsList.isEnabled = true
        urlHostsList.clear()
      }
    }

    urlHostsList.onChange?.invoke()
  }

  private class InvitationLink(title: String) : JPanel() {
    val label = JLabel(title)

    val link: JTextField = JTextField(null).apply {
      isEditable = false
      background = null
      border = null
      horizontalAlignment = SwingConstants.LEFT
      columns = 30
    }


    val copyButton = JButton(AllIcons.Actions.Copy).apply {
      toolTipText = "Copy URL"
      addActionListener {
        Toolkit
          .getDefaultToolkit()
          .systemClipboard
          .setContents(StringSelection(link.text), null)
      }
    }

    fun update(scheme: String, host: String, port: String, token: String?) {
      link.text = "${scheme}://${host}:${port}" + if (token.isNullOrEmpty()) "" else "/?token=${token}"
    }
  }
}
