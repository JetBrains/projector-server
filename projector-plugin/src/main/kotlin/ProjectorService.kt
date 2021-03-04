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
import com.intellij.diagnostic.VMOptions
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.projector.agent.AgentLauncher
import java.io.File
import java.nio.file.Path
import java.util.function.Function
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

enum class EnabledState {

  NO_VM_OPTIONS_AND_DISABLED,
  HAS_VM_OPTIONS_AND_DISABLED,
  HAS_VM_OPTIONS_AND_ENABLED,
}

class ProjectorConfig : PersistentStateComponent<ProjectorConfig> {
  var host: String? = null
  var port: String? = null

  override fun getState(): ProjectorConfig {
    return this
  }

  override fun loadState(state: ProjectorConfig) {
    host = state.host
    port = state.port
  }
}

@State(name = "Projector", storages = [Storage("ProjectorConfig.xml")])
class ProjectorService : PersistentStateComponent<ProjectorConfig> {
  private var config: ProjectorConfig = ProjectorConfig()
  private val logger = Logger.getInstance(ProjectorService::class.java)
  private val plugin = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.projector-plugin"))!!

  private var currentSession: Session? = null
    set(value) {
      field = value
      config.host = value?.host
      config.port = value?.port
    }

  private var enabled: EnabledState = when (areRequiredVmOptionsPresented()) {
    true -> EnabledState.HAS_VM_OPTIONS_AND_DISABLED
    false -> EnabledState.NO_VM_OPTIONS_AND_DISABLED
  }

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

  private fun disable() {
    if (confirmRestart("To disable Projector, restart is needed. Can I restart the IDE now?")) {
      restartIde()
    }
  }

  private fun enable() {
    attachDynamicAgent()
    enabled = EnabledState.HAS_VM_OPTIONS_AND_ENABLED
  }

  private fun areRequiredVmOptionsPresented(): Boolean {
    return System.getProperty("swing.bufferPerWindow")?.toBoolean() == false &&
           System.getProperty("jdk.attach.allowAttachSelf")?.toBoolean() == true
  }

  private fun getVMOptions(): Pair<String, File>? {
    fun getVMOptionsWriteFile(): File? {
      val writeFileMethod = VMOptions::class.java.getMethod("getWriteFile")
      return when (writeFileMethod.returnType) {
        File::class.java -> writeFileMethod.invoke(null) as File? // Pre 2020.3
        Path::class.java -> (writeFileMethod.invoke(null) as Path?)?.toUri()?.let(::File) // 2020.3
        else -> error("Unsupported IDEA version. Can't recognize signature of method VMOptions.getWriteFile.")
      }
    }

    val writeFile = getVMOptionsWriteFile()
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

  private fun getPluginPath(descriptor: IdeaPluginDescriptor): File {
    val method = try {
      IdeaPluginDescriptor::class.java.getMethod("getPluginPath")
    } catch (e: NoSuchMethodException) {
      IdeaPluginDescriptor::class.java.getMethod("getPath")
    }

    return method.invoke(descriptor) as File
  }

  private fun attachDynamicAgent() {
    val agentJar = "${getPluginPath(plugin)}/lib/projector-agent-1.0-SNAPSHOT.jar"  // todo: need to support version change
    AgentLauncher.attachAgent(agentJar)
  }

  companion object {
    private val instance: ProjectorService by lazy { ServiceManager.getService(ProjectorService::class.java)!! }

    fun enable() = instance.enable()
    fun disable() = instance.disable()
    fun activate() = instance.activate()

    fun getClientList(): Array<Array<String?>> = AgentLauncher.getClientList()
    fun disconnectAll() = AgentLauncher.disconnectAll()
    fun disconnectByIp(ip: String) = AgentLauncher.disconnectByIp(ip)

    var enabled: EnabledState
      get() = instance.enabled
      set(value) {
        instance.enabled = value
      }

    val host: String? get() = instance.config.host
    val port: String? get() = instance.config.port

    val isSessionRunning: Boolean get() = instance.currentSession != null
    var currentSession: Session
      get() {
        check(isSessionRunning) { "Current session is not available - no active sessions" }
        return instance.currentSession!!
      }
      set(value) {
        instance.currentSession = value
      }
  }

  override fun getState(): ProjectorConfig {
    return config
  }

  override fun loadState(state: ProjectorConfig) {
    config = state
  }
}
