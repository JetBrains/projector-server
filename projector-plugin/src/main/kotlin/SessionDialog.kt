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
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.projector.server.ProjectorServer
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.swing.*
import kotlin.random.Random

class SessionDialog(project: Project?) : DialogWrapper(project) {
  private val description = JLabel()
  private val myHostsList: HostsList = HostsList(ProjectorService.host)
  private val portEditor: PortEditor = PortEditor(ProjectorService.port)
  private val rwTokenEditor: TokenEditor = TokenEditor("Require password for read-write access:")
  private val roTokenEditor: TokenEditor = TokenEditor("Require password for read-only access: ")
  private val rwInvitationLink: InivitationLink = InivitationLink()
  private val roInvitationLink: InivitationLink = InivitationLink()
  private val roInvitationTitle = JLabel("Read Only Link:")

  val rwToken: String? get() = rwTokenEditor.token
  val roToken: String? get() = roTokenEditor.token
  val host: String get() = myHostsList.selected
  val port: String get() = portEditor.value

  init {
    if (ProjectorService.isSessionRunning) {
      title = "Edit Current Session Parameters"
      description.text = "<html>The current session has already started.<br>Do you want to change passwords?"
      myOKAction.putValue(Action.NAME, "Save")

      myHostsList.isEnabled = false
      portEditor.isEnabled = false

      rwTokenEditor.token = ProjectorService.currentSession.rwToken
      roTokenEditor.token = ProjectorService.currentSession.roToken
    } else {
      title = "Start Remote Access to IDE"
      description.text = "<html>Config remote access to IDE.<br>Please check your connection parameters:"
      myOKAction.putValue(Action.NAME, "Start")

      rwTokenEditor.token = generatePassword()
      roTokenEditor.token = generatePassword()
    }

    myHostsList.onChange = ::updateInvitationLinks
    portEditor.onChange = ::updateInvitationLinks
    rwTokenEditor.onChange = ::updateInvitationLinks
    roTokenEditor.onChange = ::updateInvitationLinks

    updateInvitationLinks()
    setResizable(false)
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val hostPanel = JPanel()
    LinearPanelBuilder(hostPanel)
      .addNextComponent(myHostsList, weightx = 0.5, rightGap = 35)
      .addNextComponent(portEditor, weightx = 0.5)

    val panel = JPanel()
    LinearPanelBuilder(panel)
      .addNextComponent(description, gridWidth = 2, bottomGap = 5)
      .startNextLine().addNextComponent(hostPanel, 2)
      .startNextLine().addNextComponent(rwTokenEditor, 2)
      .startNextLine().addNextComponent(roTokenEditor, 2)
      .startNextLine().addNextComponent(JLabel("Invitation Links:"), gridWidth = 2, topGap = 5, bottomGap = 5)
      .startNextLine().addNextComponent(JLabel("Full Access Link:")).addNextComponent(rwInvitationLink)
      .startNextLine().addNextComponent(roInvitationTitle).addNextComponent(roInvitationLink)
    return panel
  }

  private fun updateInvitationLinks() {
    rwInvitationLink.update(host, port, rwTokenEditor.token)
    roInvitationLink.update(host, port, roTokenEditor.token)

    if (rwTokenEditor.token == roTokenEditor.token) {
      // Hide RO invitation section when tokens are equal - user will always get RW access in this case.
      roInvitationTitle.isVisible = false
      roInvitationLink.isVisible = false
    } else {
      roInvitationTitle.isVisible = true
      roInvitationLink.isVisible = true
    }
  }

  private class InivitationLink() : JPanel() {
    private val copyButton = JButton(AllIcons.Actions.Copy).apply {
      addActionListener {
        Toolkit
          .getDefaultToolkit()
          .systemClipboard
          .setContents(StringSelection(link.text), null)
      }
    }

    private val link: JTextField = JTextField(null).apply {
      isEditable = false
      background = null
      border = null
      columns = 30
    }

    init {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      add(link)
      add(copyButton)
    }

    fun update(host: String, port: String, token: String?) {
      link.text = "http://${host}:${port}" + if (token == null) "" else "/?token=${token}"
    }
  }

  private class TokenEditor(title: String) : JPanel() {
    private val requiredCheckBox: JCheckBox = JCheckBox(title).apply {
      addActionListener {
        tokenTextField.text = if (isSelected) generatePassword() else null
        tokenTextField.isEnabled = isSelected
        onChange?.invoke()
      }
    }
    private val tokenTextField: JTextField = JTextField().apply {
      columns = 15

      addKeyListener(object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent) {
          onChange?.invoke()
        }
      })
    }

    init {
      LinearPanelBuilder(this)
        .addNextComponent(requiredCheckBox)
        .addNextComponent(tokenTextField)
    }

    var onChange: (() -> Unit)? = null

    var token
      get() = if (requiredCheckBox.isSelected) tokenTextField.text else null
      set(value) {
        tokenTextField.text = value
        requiredCheckBox.isSelected = value != null
    }
  }

  private class HostsList(selectedHost: String?) : JPanel() {
    private val title = JLabel("Host:")
    private val hosts: JComboBox<String> = ComboBox<String>().apply {
      val dockerVendor = byteArrayOf(0x02.toByte(), 0x42.toByte())
      NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filterNotNull()
        .filterNot { it.isLoopback }
        .filterNot {
          it.hardwareAddress != null
          &&
          it.hardwareAddress.sliceArray(0..1).contentEquals(dockerVendor)
        }
        .flatMap { it.interfaceAddresses?.asSequence()?.filterNotNull() ?: emptySequence() }
        .mapNotNull { (it.address as? Inet4Address)?.hostName }
        .forEach(::addItem)

      selectedHost?.takeIf(String::isNotEmpty)?.let { selectedItem = it }
      addActionListener { onChange?.invoke() }
    }

    init {
      LinearPanelBuilder(this)
        .addNextComponent(title, weightx = 0.1)
        .addNextComponent(hosts)
    }

    var onChange: (() -> Unit)? = null

    val selected get() = hosts.selectedItem as? String ?: ""

    override fun setEnabled(enabled: Boolean) {
      super.setEnabled(enabled)
      hosts.isEnabled = enabled
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
        .addNextComponent(title, weightx = 0.1)
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
    private fun generatePassword(): String {
      val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
      return (1..11)
        .map { Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
    }
  }
}
