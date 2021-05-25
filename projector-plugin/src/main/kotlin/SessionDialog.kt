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
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.projector.server.ProjectorServer
import org.jetbrains.projector.server.util.AsyncHostResolver
import org.jetbrains.projector.server.util.Host
import org.jetbrains.projector.server.util.ResolvedHostSubscriber
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.swing.*
import kotlin.random.Random

class SessionDialog(project: Project?) : DialogWrapper(project) {
  private val description = JLabel()
  private val resolver: AsyncHostResolver = AsyncHostResolver()
  private val myHostsList: HostsList = HostsList("Host:", ProjectorService.host)
  private val urlHostsList = HostsList("URL: ", null)
  private val connectionPanel = ConnectionPanel(resolver)
  private val portEditor = PortEditor(ProjectorService.port)
  private val rwTokenEditor = TokenEditor("Password for read-write access:",
                                          ProjectorService.rwToken ?: generatePassword())
  private val roTokenEditor = TokenEditor("Password for read-only  access:",
                                          ProjectorService.roToken ?: generatePassword())
  private val requireConnectConfirmation: JCheckBox = JCheckBox("Require connection confirmation", ProjectorService.confirmConnection)
  private val autostartProjector: JCheckBox = JCheckBox("Start Projector automatically when ${productName()} starts",
                                                        ProjectorService.autostart)
  private val rwInvitationLink = InvitationLink("Read/Write Link:")
  private val roInvitationLink = InvitationLink("Read Only  Link:")

  val rwToken: String get() = rwTokenEditor.token
  val roToken: String get() = roTokenEditor.token
  val listenAddress: String get() = myHostsList.selected?.address ?: ""
  val listenPort: String get() = portEditor.value
  val confirmConnection: Boolean get() = requireConnectConfirmation.isSelected
  val autostart: Boolean get() = autostartProjector.isSelected
  private val urlAddress: String get() = urlHostsList.selected?.address ?: ""

  init {

    if (ProjectorService.isSessionRunning) {
      title = "Edit Current Session Parameters"
      description.text = "<html>The current session has already started.<br>Do you want to change settings?"
      myOKAction.putValue(Action.NAME, "Save")

      portEditor.isEnabled = false
      myHostsList.isEnabled = false
    }
    else {
      title = "Start Remote Access to IDE"
      description.text = "<html>Config remote access to IDE.<br>Listen on:"
      myOKAction.putValue(Action.NAME, "Start")
    }

    myHostsList.onChange = ::updateURLList
    urlHostsList.onChange = ::updateInvitationLinks
    portEditor.onChange = ::updateInvitationLinks
    rwTokenEditor.onChange = ::updateInvitationLinks
    roTokenEditor.onChange = ::updateInvitationLinks

    updateURLList()
    updateInvitationLinks()
    setResizable(false)
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

      .startNextLine().addNextComponent(requireConnectConfirmation, topGap = 5, bottomGap = 5)

      .startNextLine()
      .addNextComponent(autostartProjector, topGap = 5, bottomGap = 5)

      .startNextLine().addNextComponent(JLabel("Invitation Links:"), topGap = 5, bottomGap = 5)

      .startNextLine().addNextComponent(urlHostsList, gridWidth = 7)

      .startNextLine().addNextComponent(rwInvitationLink, gridWidth = 7)
      .addNextComponent(rwInvitationLink.copyButton, gridWidth = 1)

      .startNextLine().addNextComponent(roInvitationLink, gridWidth = 7)
      .addNextComponent(roInvitationLink.copyButton, gridWidth = 1)

      .startNextLine().addNextComponent(connectionPanel, gridWidth = 8)

    return panel
  }

  private fun updateInvitationLinks() {
    rwInvitationLink.update(urlAddress, listenPort, rwTokenEditor.token)
    roInvitationLink.update(urlAddress, listenPort, roTokenEditor.token)
  }

  fun cancelResolverRequests() {
    resolver.cancelPendingRequests()
  }

  private fun updateURLList() {
    val host = myHostsList.selected

    when {
      host == ALL_HOSTS -> {
        urlHostsList.setTooltip(null)
        val oldValue = urlHostsList.selected
        val hostList = getHostList { ip -> resolver.resolve(urlHostsList, ip) }
        urlHostsList.setItems(hostList)
        urlHostsList.selected = oldValue
        urlHostsList.isEnabled = hostList.size > 1
      }
      host != null -> {
        urlHostsList.setItems(listOf(host))
        urlHostsList.selected = host
        urlHostsList.setTooltip("nothing to select from")
        urlHostsList.isEnabled = false
        resolver.resolve(myHostsList, host.address)
        resolver.resolve(urlHostsList, host.address)
      }
      else -> {
        urlHostsList.setTooltip(null)
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


    fun update(host: String, port: String, token: String?) {
      link.text = "http://${host}:${port}" + if (token.isNullOrEmpty()) "" else "/?token=${token}"
    }
  }

  private class TokenEditor(title: String, token: String) {
    val label = JLabel(title)
    val tokenTextField: JTextField = JTextField(token).apply {
      columns = RANDOM_PASSWORD_LEN
      addKeyListener(object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent) {
          onChange?.invoke()
        }
      })
    }

    val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
      toolTipText = "Generate random password"
      addActionListener {
        tokenTextField.text = generatePassword()
        onChange?.invoke()
      }
    }

    var onChange: (() -> Unit)? = null

    var token: String
      get() = tokenTextField.text
      set(value) {
        tokenTextField.text = value
      }
  }

  private inner class HostsList(label: String, selectedHost: String?) : JPanel(), ResolvedHostSubscriber {
    private val title = JLabel(label)
    private val hosts: JComboBox<Host> = ComboBox<Host>().apply {
      val hosts = listOf(ALL_HOSTS) + getHostList { ip -> resolver.resolve(this@HostsList, ip) }

      hosts.forEach(::addItem)
      selectedHost?.let { selectedHost ->
        selectedItem = hosts.find { it.address == selectedHost }
      }

      addActionListener { onChange?.invoke() }
    }

    fun clear() = hosts.removeAllItems()

    fun addItems(values: List<Host>) = values.forEach { hosts.addItem(it) }

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

    fun setTooltip(text: String?) {
      hosts.toolTipText = text
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
  }

  private class PortEditor(portValue: String?) : JPanel() {
    private val title = JLabel("Port:")
    private val port: JTextField = JTextField().apply {
      text = portValue?.takeIf(String::isNotEmpty) ?: ProjectorServer.getEnvPort().toString()

      addKeyListener(object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent) {
          onChange?.invoke()
        }
      })
    }

    init {
      LinearPanelBuilder(this)
        .addNextComponent(title, gridWidth = 2)
        .addNextComponent(port)
    }

    var onChange: (() -> Unit)? = null
    val value get() = port.text ?: ""

    override fun setEnabled(enabled: Boolean) {
      super.setEnabled(enabled)
      port.isEnabled = enabled
    }
  }

  companion object {
    private val ALL_HOSTS = Host("0.0.0.0", "all addresses")
    private val dockerVendor = byteArrayOf(0x02.toByte(), 0x42.toByte())

    private const val RANDOM_PASSWORD_LEN = 11

    private fun generatePassword(): String {
      val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
      return (1..RANDOM_PASSWORD_LEN)
        .map { Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
    }

    private fun getHostList(toHost: (ip: InetAddress) -> Host) = NetworkInterface.getNetworkInterfaces()
      .asSequence()
      .filterNotNull()
      .filterNot {
        it.hardwareAddress != null && it.hardwareAddress.sliceArray(0..1).contentEquals(dockerVendor)
      } // drop docker
      .flatMap { it.interfaceAddresses?.asSequence()?.filterNotNull() ?: emptySequence() }
      .filterNot { it.address is Inet6Address } // drop IP v 6
      .map { toHost(it.address) }
      .toList()
  }
}
