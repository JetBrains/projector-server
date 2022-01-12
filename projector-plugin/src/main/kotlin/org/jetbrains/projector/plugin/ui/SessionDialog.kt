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
import com.intellij.ui.components.Link
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
  private val rwTokenEditor = TokenEditor("Password for read-write access:", ProjectorService.rwToken)
  private val roTokenEditor = TokenEditor("Password for read-only  access:", ProjectorService.roToken)
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
  private val roInvitationLink = InvitationLink("Read Only  Link:")

  val rwToken: String get() = rwTokenEditor.token
  val roToken: String get() = roTokenEditor.token
  val listenAddress: String get() = myHostsList.selected?.address ?: ""
  val listenPort: String get() = portEditor.value
  private val urlAddress: String
    get() {
      if (useNamesInURLs.isSelected) {
        return urlHostsList.selected?.name ?: ""
      }

      return urlHostsList.selected?.address ?: ""
    }

  val useSecureConnection: Boolean get() = secureConnection.isSelected

  init {

    if (isConnectionSettingsEditable()) {
      title = "Start Remote Access to IDE"
      description.text = "<html>Config remote access to IDE.<br>Listen on:"
      myOKAction.putValue(Action.NAME, "Start")
    }
    else {
      title = "Current Session"
      description.text = "<html>The current session has already started.<br>Do you want to change settings?"
      myOKAction.putValue(Action.NAME, "Save")

      portEditor.isEnabled = false
      myHostsList.isEnabled = false
      secureConnection.isEnabled = false
    }

    myHostsList.onChange = ::updateURLList
    urlHostsList.onChange = ::updateInvitationLinks
    portEditor.onChange = ::updateInvitationLinks
    rwTokenEditor.onChange = ::updateInvitationLinks
    roTokenEditor.onChange = ::updateInvitationLinks

    updateURLList()
    updateInvitationLinks()
    isResizable = false
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel()
    LinearPanelBuilder(panel).addNextComponent(description, bottomGap = 5)
      .startNextLine()
      .addNextComponent(myHostsList, gridWidth = 7)
      .addNextComponent(portEditor)

      .startNextLine()
      .addNextComponent(rwTokenEditor.label, gridWidth = 4)
      .addNextComponent(JLabel(), gridWidth = 1)
      .addNextComponent(rwTokenEditor.tokenTextField, gridWidth = 2)
      .addNextComponent(rwTokenEditor.refreshButton, gridWidth = 1)

      .startNextLine()
      .addNextComponent(roTokenEditor.label, gridWidth = 4)
      .addNextComponent(JLabel(), gridWidth = 1)
      .addNextComponent(roTokenEditor.tokenTextField, gridWidth = 2)
      .addNextComponent(roTokenEditor.refreshButton, gridWidth = 1)

      .startNextLine().addNextComponent(secureConnection, topGap = 5, bottomGap = 5)
      .startNextLine().addNextComponent(useNamesInURLs, topGap = 5, bottomGap = 5)

      .startNextLine().addNextComponent(JLabel("Invitation Links:"), topGap = 5, bottomGap = 5)

      .startNextLine().addNextComponent(urlHostsList, gridWidth = 7)

      .startNextLine().addNextComponent(rwInvitationLink, gridWidth = 7)
      .addNextComponent(rwInvitationLink.copyButton, gridWidth = 1)

      .startNextLine().addNextComponent(roInvitationLink, gridWidth = 7)
      .addNextComponent(roInvitationLink.copyButton, gridWidth = 1)

      .startNextLine().addNextComponent(ConnectionPanel(), gridWidth = 8)
      .startNextLine().addNextComponent(Link("Settings") { openSettings() })

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


    rwTokenEditor.token = ProjectorService.rwToken
    roTokenEditor.token = ProjectorService.roToken

    updateInvitationLinks()
  }

  private fun updateInvitationLinks() {
    val scheme = if (useSecureConnection) "https" else "http"
    rwInvitationLink.update(scheme, urlAddress, listenPort, rwTokenEditor.token)
    roInvitationLink.update(scheme, urlAddress, listenPort, roTokenEditor.token)
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
      columns = 35
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

    init {
      LinearPanelBuilder(this)
        .addNextComponent(label)
        .addNextComponent(link)
    }

    fun update(scheme: String, host: String, port: String, token: String?) {
      link.text = "${scheme}://${host}:${port}" + if (token.isNullOrEmpty()) "" else "/?token=${token}"
    }
  }
}
