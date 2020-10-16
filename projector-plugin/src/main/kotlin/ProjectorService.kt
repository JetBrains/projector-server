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
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.function.Function

class ProjectorConfig : PersistentStateComponent<ProjectorConfig> {
  var enabled: Boolean = false
  override fun getState(): ProjectorConfig? {
    return this
  }

  override fun loadState(state: ProjectorConfig) {
    enabled = state.enabled
  }

}

@State(name = "Projector", storages = [Storage("ProjectorConfig.xml")])
class ProjectorService : PersistentStateComponent<ProjectorConfig> {
  private var config: ProjectorConfig = ProjectorConfig()
  private val logger = Logger.getInstance(ProjectorService::class.java)
  private val plugin = PluginManager.getPlugin(PluginId.getId("org.jetbrains.projector-plugin"))!!


  private fun vmoptions(): File? {
    return try {
      VMOptions.getWriteFile()?.also { logger.info("vmoptions: ${it.absolutePath}") }
    }
    catch (ex: Throwable) {
      logger.warn("Failed to read vmoptions: $ex")
      null
    }
  }

  val enabled: Boolean
    get() = config.enabled


  fun disable() {
    if (!confirmRestart()) return

    getVMOptions()?.let { (content, writeFile) ->
      content
        .lineSequence()
        .filterNot { it.startsWith("-javaagent:") && it.contains("projector-plugin") }
        .joinToString(separator = System.lineSeparator())
        .let { FileUtil.writeToFile(writeFile, it) }

      config.enabled = false
      exit()
    }
  }

  fun enable() {
    if (!confirmRestart()) return

    val agentJar = "${plugin.path}/lib/projector-agent-${plugin.version}.jar"
    val agentOption = "-javaagent:$agentJar=$agentJar"
    logger.warn("agentOption: $agentOption")

    getVMOptions()?.let { (content, writeFile) ->
      content
        .lineSequence()
        .filterNot { it.startsWith("-javaagent:") && it.contains("projector-plugin") }
        .plus(agentOption)
        .joinToString(separator = System.lineSeparator())
        .let { FileUtil.writeToFile(writeFile, it) }

      config.enabled = true
      exit()
    }
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

  private fun confirmRestart() : Boolean {
    val title = "Restart to ${if (enabled) "disable" else "enable"} Projector"
    val message = Function<String, String> { action: String? ->
      "$action ${ApplicationNamesInfo.getInstance().fullProductName} to ${if (enabled) "remove" else "add"} java agent?"}
    return PluginManagerConfigurable.showRestartDialog(title, message) == Messages.YES
  }


  private fun exit() {
    (ApplicationManager.getApplication() as ApplicationEx).restart(true)
  }

  companion object {
    val instance: ProjectorService by lazy { ServiceManager.getService(ProjectorService::class.java)!! }
  }

  override fun getState(): ProjectorConfig? {
    return config
  }

  override fun loadState(state: ProjectorConfig) {
    config = state
  }
}
