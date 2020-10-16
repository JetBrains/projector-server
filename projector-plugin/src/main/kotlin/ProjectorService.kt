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
import com.intellij.diagnostic.VMOptions
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.projector.agent.AgentLauncher
import java.io.File
import java.util.function.Function
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

enum class EnabledState {

  NO_VM_OPTIONS_AND_DISABLED,
  HAS_VM_OPTIONS_AND_DISABLED,
  HAS_VM_OPTIONS_AND_ENABLED,
}

object ProjectorService {

  private val logger = Logger.getInstance(ProjectorService::class.java)
  private val plugin = PluginManager.getPlugin(PluginId.getId("org.jetbrains.projector-plugin"))!!

  var enabled: EnabledState = when (areRequiredVmOptionsPresented()) {
    true -> EnabledState.HAS_VM_OPTIONS_AND_DISABLED
    false -> EnabledState.NO_VM_OPTIONS_AND_DISABLED
  }
    private set

  fun activate() {
    if (confirmRestart(
        "Before enabling Projector for the first time, some run arguments (VM properties) should be set. Can I set them and restart the IDE now?")) {
      getVMOptions()?.let { (content, writeFile) ->
        content
          .lineSequence()
          .filterNot { it.startsWith("-Dswing.bufferPerWindow") || it.startsWith("-Djdk.attach.allowAttachSelf") }
          .plus("-Dswing.bufferPerWindow=false")
          .plus("-Djdk.attach.allowAttachSelf=true")
          .joinToString(separator = System.lineSeparator())
          .let { FileUtil.writeToFile(writeFile, it) }

        restartIde()
      } ?: SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(
          null,
          "Can't change VM options. Please see logs to understand the error",
          "Can't set up...",
          JOptionPane.ERROR_MESSAGE,
        )
      }
    }
  }

  fun disable() {
    if (confirmRestart("To disable Projector, restart is needed. Can I restart the IDE now?")) {
      restartIde()
    }
  }

  fun enable() {
    attachDynamicAgent()
    enabled = EnabledState.HAS_VM_OPTIONS_AND_ENABLED
  }

  private fun areRequiredVmOptionsPresented(): Boolean {
    return System.getProperty("swing.bufferPerWindow")?.toBoolean() == false &&
           System.getProperty("jdk.attach.allowAttachSelf")?.toBoolean() == true
  }

  private fun getVMOptions(): Pair<String, File>? {
    val writeFile = VMOptions.getWriteFile()
    if (writeFile == null) {
      logger.warn("VM options file not configured")
      return null
    }

    val templateFile = VMOptions.read()
    if (templateFile == null) {
      logger.warn("VM options file not configured")
      return null
    }

    val s = if (writeFile.exists()) {
      writeFile.readText()
    }
    else {
      templateFile
    }

    return Pair(s, writeFile)
  }

  private fun confirmRestart(messageString: String): Boolean {
    val title = "Restart is needed..."
    val message = Function<String, String> { messageString }
    return PluginManagerConfigurable.showRestartDialog(title, message) == Messages.YES
  }

  private fun restartIde() {
    (ApplicationManager.getApplication() as ApplicationEx).restart(true)
  }

  private fun attachDynamicAgent() {
    val agentJar = "${plugin.path}/lib/projector-agent-${plugin.version}.jar"
    AgentLauncher.attachAgent(agentJar)
  }
}
