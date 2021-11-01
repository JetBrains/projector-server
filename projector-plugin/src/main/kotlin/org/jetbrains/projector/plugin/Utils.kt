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

package org.jetbrains.projector.plugin

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.PathUtil
import org.jetbrains.projector.agent.AgentLauncher
import org.jetbrains.projector.awt.PToolkit
import org.w3c.dom.Node
import java.awt.Toolkit
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import java.util.function.Function
import java.io.File
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun productName(): String = ApplicationInfo.getInstance().versionName

fun getPathToPluginDir() = File(PathUtil.getJarPathForClass(ProjectorService::class.java)).parentFile.toString()

private const val VERSIONS_FILE_PATH = "/META-INF/pluginVersions.txt"
private const val AGENT_VERSION = "agentVersion"

fun getAgentVersion(): String {
  val content = loadVersionsContent()
  val map = parseVersions(content)
  val result = map[AGENT_VERSION]
  require(result != null) { "Can't get agent version from $VERSIONS_FILE_PATH: $map" }

  return result
}

fun getIdeStatusBar(project: Project): StatusBar? {
  val frame = WindowManager.getInstance().getIdeFrame(project) ?: return null
  return WindowManager.getInstance().getStatusBar(frame.component, project)
}


private fun loadVersionsContent() = ProjectorService::class.java.getResource(VERSIONS_FILE_PATH)!!.readText()

private fun parseVersions(content: String): Map<String, String> {
  return content.split("\n")
    .filter { it.isNotEmpty() }
    .map { it.split("=") }
    .filter { it.size == 2 }
    .associate { (name, version) -> name to version }
}

fun isHeadlessProjectorDetected() = Toolkit.getDefaultToolkit()::class.toString() == PToolkit::class.toString()

fun areRequiredVmOptionsPresented(): Boolean {
  return System.getProperty("swing.bufferPerWindow")?.toBoolean() == false &&
         System.getProperty("jdk.attach.allowAttachSelf")?.toBoolean() == true
}

fun setSystemProperty(name: String, value: String?) {
  if (value == null) {
    System.clearProperty(name)
  }
  else {
    System.setProperty(name, value)
  }
}

fun isActivationNeeded() = ProjectorService.enabled == EnabledState.NO_VM_OPTIONS_AND_DISABLED

fun isProjectorRunning() = ProjectorService.enabled == EnabledState.HAS_VM_OPTIONS_AND_ENABLED

fun isProjectorAutoStarting(): Boolean {
  return !isHeadlessProjectorDetected()
         &&
         ProjectorService.autostart
         &&
         ProjectorService.enabled == EnabledState.HAS_VM_OPTIONS_AND_DISABLED
}

fun isProjectorDisabled(): Boolean {
  return !isHeadlessProjectorDetected()
         &&
         !ProjectorService.autostart
         &&
         ProjectorService.enabled == EnabledState.HAS_VM_OPTIONS_AND_DISABLED
}

fun isProjectorStopped() = ProjectorService.enabled == EnabledState.STOPPED

fun confirmRestart(messageString: String): Boolean {
  val title = "Restart Is Needed..."
  val message = Function<String, String> { messageString }
  return PluginManagerConfigurable.showRestartDialog(title, message) == Messages.YES
}

fun restartIde() {
  (ApplicationManager.getApplication() as ApplicationEx).restart(true)
}

fun attachDynamicAgent() {
  val agentJar = "${getPathToPluginDir()}/projector-agent-${getAgentVersion()}.jar"
  AgentLauncher.attachAgent(agentJar)
}

private const val SUBSYSTEM = "PROJECTOR_SERVICE_CONFIG"
const val PROJECTOR_RW_TOKEN_KEY = "PROJECTOR_RW_TOKEN"
const val PROJECTOR_RO_TOKEN_KEY = "PROJECTOR_RO_TOKEN"

fun loadToken(key: String): String? {
  val credentialAttributes = createCredentialAttributes(key)
  return PasswordSafe.instance.getPassword(credentialAttributes)
}

fun storeToken(key: String, value: String?) {
  val attributes = createCredentialAttributes(key)
  val credentials = Credentials(user = key, password = value)
  PasswordSafe.instance.set(attributes, credentials)
}

fun migrateTokensToSecureStorage() {
  try {
    copyTokenToSecureStorage(PROJECTOR_RW_TOKEN_KEY, "rwToken")
    copyTokenToSecureStorage(PROJECTOR_RO_TOKEN_KEY, "roToken")
    removeTokensFromXmlStorage()
  }
  catch (e: Exception) {
    // Something wrong is happened: broken XML, locked file, etc
    // We can't recover such error - just log it and try migrate tokens next time
    val logger = Logger.getInstance("migrateTokensToSecureStorage")
    logger.warn("Can't migrate tokens to secure storage: $e")
  }
}

private fun createCredentialAttributes(key: String) = CredentialAttributes(generateServiceName(SUBSYSTEM, key))

private fun loadTokenFromXmlStorage(tokenName: String): String? {
  val file = Paths.get(PathManager.getOptionsPath(), ProjectorConfig.STORAGE_NAME).toFile()
  var result: String? = null

  if (file.exists()) {
    val factory = DocumentBuilderFactory.newInstance()

    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(file)
    val elements = doc.getElementsByTagName("option")

    for (i in 0 until elements.length) {
      val e = elements.item(i)
      val attrs = e.attributes
      val name = attrs.getNamedItem("name")

      if (name.nodeValue == tokenName) {
        val token = attrs.getNamedItem("value")
        result = token.nodeValue
        break
      }
    }
  }

  return result
}

private fun removeTokensFromXmlStorage() {
  val file = Paths.get(PathManager.getOptionsPath(), ProjectorConfig.STORAGE_NAME).toFile()
  val factory = DocumentBuilderFactory.newInstance()

  if (file.exists()) {
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(file)
    val elements = doc.getElementsByTagName("option")
    val toRemove: ArrayList<Node> = arrayListOf()

    for (i in 0 until elements.length) {
      val e = elements.item(i)

      if (isTokenNode(e)) {
        toRemove.add(e)
      }
    }

    toRemove.forEach { it.parentNode.removeChild(it) }

    if (toRemove.isNotEmpty()) {
      writeXml(doc, file)
    }
  }
}

private fun copyTokenToSecureStorage(key: String, tokenName: String) {
  val token = loadTokenFromXmlStorage(tokenName)
  token?.let {
    storeToken(key, it)
  }
}

private fun isTokenNode(n: Node): Boolean {
  val attrs = n.attributes
  val nameNode = attrs.getNamedItem("name")
  val name = nameNode.nodeValue

  return name == "roToken" || name == "rwToken"
}

private fun writeXml(doc: Document, file: File) {
  val factory = TransformerFactory.newInstance()
  val transformer = factory.newTransformer()
  transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
  transformer.setOutputProperty(OutputKeys.INDENT, "yes")
  val out = StreamResult(file)
  transformer.transform(DOMSource(doc), out)
}
