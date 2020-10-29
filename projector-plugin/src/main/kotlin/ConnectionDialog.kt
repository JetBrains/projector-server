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
import com.intellij.util.containers.toArray
import java.awt.ComponentOrientation
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.swing.*
import kotlin.random.Random

class ConnectionDialog(project: Project?) : DialogWrapper(project) {
  private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
  private var panelWrapper: JPanel = JPanel()
  val host: JComboBox<String> = ComboBox(getHosts())
  val port: JTextField = JTextField(Utils.getPort())
  val tokenRW: JTextField = JTextField(getSecret())
  val tokenRO: JTextField = JTextField(getSecret())
  private val copyRW = JButton(AllIcons.Actions.Copy)
  private val copyRO = JButton(AllIcons.Actions.Copy)
  private val urlRW: JLabel = JLabel()
  private val urlRO: JLabel = JLabel()

  init {
    title = "Start Remote Access to IDE"
    setResizable(false)
    init()

    if (!ProjectorService.instance.host.isNullOrEmpty()) {
      host.selectedItem = ProjectorService.instance.host
    }

    if (!ProjectorService.instance.port.isNullOrEmpty()) {
      port.text = ProjectorService.instance.port
    }

    updateUrls()

    val keyListener: KeyListener = object : KeyAdapter() {
      override fun keyReleased(e: KeyEvent) {
        updateUrls()
      }
    }
    port.addKeyListener(keyListener)
    tokenRW.addKeyListener(keyListener)
    tokenRO.addKeyListener(keyListener)

    host.addActionListener {
      updateUrls()
    }

    copyRW.addActionListener {
      Utils.copyToClipboard(urlRW.text)
    }

    copyRO.addActionListener {
      Utils.copyToClipboard(urlRO.text)
    }
  }

  override fun createDefaultActions() {
    super.createDefaultActions()
    myOKAction.putValue(Action.NAME, "Start")
  }

  override fun createCenterPanel(): JComponent? {
    panelWrapper.componentOrientation = ComponentOrientation.LEFT_TO_RIGHT
    panelWrapper.layout = GridBagLayout()

    val constraints = GridBagConstraints()
    constraints.fill = GridBagConstraints.HORIZONTAL

    constraints.ipady = 10
    constraints.gridwidth = 4
    constraints.gridx = 0
    constraints.gridy = 0
    panelWrapper.add(JLabel("<html>Do you want to provide remote access to IDE?<br>Please check your connection parameters:"),
                     constraints)

    constraints.ipady = 0
    constraints.gridwidth = 1
    constraints.gridx = 0
    constraints.gridy = 1
    panelWrapper.add(JLabel("Host:"), constraints)

    constraints.gridx = 0
    constraints.gridy = 2
    panelWrapper.add(JLabel("Port:"), constraints)

    constraints.gridx = 0
    constraints.gridy = 3
    panelWrapper.add(JLabel("Secret read-write:"), constraints)

    constraints.gridx = 0
    constraints.gridy = 4
    panelWrapper.add(JLabel("Secret read-only:"), constraints)

    constraints.gridwidth = 2
    constraints.gridx = 1
    constraints.gridy = 1
    panelWrapper.add(host, constraints)

    constraints.gridx = 1
    constraints.gridy = 2
    panelWrapper.add(port, constraints)

    constraints.gridx = 1
    constraints.gridy = 3
    panelWrapper.add(tokenRW, constraints)

    constraints.gridx = 1
    constraints.gridy = 4
    panelWrapper.add(tokenRO, constraints)

    constraints.ipady = 10
    constraints.gridwidth = 1
    constraints.gridx = 0
    constraints.gridy = 5
    panelWrapper.add(JLabel("Full Access URL:"), constraints)

    constraints.gridx = 0
    constraints.gridy = 6
    panelWrapper.add(JLabel("View Only URL:"), constraints)

    constraints.gridwidth = 2
    constraints.gridx = 1
    constraints.gridy = 5
    panelWrapper.add(urlRW, constraints)

    constraints.gridwidth = 2
    constraints.gridx = 1
    constraints.gridy = 6
    panelWrapper.add(urlRO, constraints)

    constraints.ipadx = 0
    constraints.ipady = 0
    constraints.gridwidth = 0
    constraints.gridx = 3
    constraints.gridy = 5
    panelWrapper.add(copyRW, constraints)

    constraints.gridx = 3
    constraints.gridy = 6
    panelWrapper.add(copyRO, constraints)

    return panelWrapper
  }

  private fun getHosts(): Array<String> {
    val dockerVendor = byteArrayOf(0x02.toByte(), 0x42.toByte())
    val arr = emptyArray<String>()

    return NetworkInterface.getNetworkInterfaces()
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
      .toList()
      .toArray(arr)
  }

  private fun getSecret(): String {
    return (1..11)
      .map { Random.nextInt(0, charPool.size) }
      .map(charPool::get)
      .joinToString("")
  }

  private fun getUrl(token: String?): String {
    return Utils.getUrl(host.selectedItem as String, port.text, token ?: "")
  }

  private fun updateUrls() {
    urlRW.text = getUrl(tokenRW.text)
    urlRO.text = getUrl(tokenRO.text)
  }
}
