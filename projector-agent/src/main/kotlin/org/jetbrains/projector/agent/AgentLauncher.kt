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
package org.jetbrains.projector.agent

import com.sun.tools.attach.VirtualMachine
import org.jetbrains.projector.agent.GraphicsTransformer.Companion.DRAW_HANDLER_CLASS
import org.jetbrains.projector.util.loading.unprotect
import java.beans.PropertyChangeListener
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.util.*

public object AgentLauncher {

  private fun checkProperty(name: String, expected: String) {
    val actual = System.getProperty(name)
    if (actual != expected) {
      println("System property `$name` is incorrect: expected <$expected>, got <$actual>")
    }
  }

  private fun getNotNullProperty(name: String): String {
    return requireNotNull(System.getProperty(name)) { "Can't launch: no system property `$name` defined..." }
  }

  private fun getMainMethodOf(canonicalClassName: String): Method {
    val mainClass = Class.forName(canonicalClassName)
    return mainClass.getMethod("main", Array<String>::class.java)
  }

  @JvmStatic
  public fun attachAgent(agentJar: String) {
    println("dynamically attaching agent...")

    checkProperty("swing.bufferPerWindow", false.toString())
    checkProperty("jdk.attach.allowAttachSelf", true.toString())

    val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().name
    val pid = nameOfRunningVM.substringBefore('@')

    try {
      val vm = VirtualMachine.attach(pid)!!
      vm.loadAgent(agentJar, agentJar)
      vm.detach()
    }
    catch (e: Exception) {
      throw RuntimeException(e)
    }

    println("dynamically attaching agent... - done")
  }

  /**
   * Get ProjectorClassLoader instance via AppClassLoader
   */
  private val classLoader: ClassLoader by lazy {
    // language=java prefix="import " suffix=;
    val projectorClassLoaderName = "org.jetbrains.projector.util.loading.ProjectorClassLoader"

    ClassLoader
      .getSystemClassLoader()
      .loadClass(projectorClassLoaderName)
      .getDeclaredMethod("getInstance")
      .apply(Method::unprotect)
      .invoke(null) as ClassLoader
  }

  private fun getHandlerClass(): Class<*> {
    return classLoader.loadClass(DRAW_HANDLER_CLASS)
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")  // KTIJ-18982
  private val getClientListMethod by lazy { getHandlerClass().getMethod("getClientList")!! }

  @JvmStatic
  public fun getClientList(): Array<Array<String?>> {
    @Suppress("UNCHECKED_CAST")
    return getClientListMethod.invoke(null) as Array<Array<String?>>
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")  // KTIJ-18982
  private val disconnectAllMethod by lazy { getHandlerClass().getMethod("disconnectAll")!! }

  @JvmStatic
  public fun disconnectAll() {
    disconnectAllMethod.invoke(null)
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")  // KTIJ-18982
  private val disconnectByIpMethod by lazy { getHandlerClass().getMethod("disconnectByIp", String::class.java)!! }

  @JvmStatic
  public fun disconnectByIp(ip: String) {
    disconnectByIpMethod.invoke(null, ip)
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")  // KTIJ-18982
  private val addObserverMethod by lazy { getHandlerClass().getMethod("addObserver", PropertyChangeListener::class.java)!! }

  @JvmStatic
  public fun addObserver(listener: PropertyChangeListener) {
    addObserverMethod.invoke(null, listener)
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")  // KTIJ-18982
  private val removeObserverMethod by lazy {
    getHandlerClass().getMethod("removeObserver", PropertyChangeListener::class.java)!!
  }

  @JvmStatic
  public fun removeObserver(listener: PropertyChangeListener) {
    try {
      removeObserverMethod.invoke(null, listener)
    }
    catch (e: ClassNotFoundException) {
      println("Class GraphicsInterceptor not found: agent wasn't injected.")
    }
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")  // KTIJ-18982
  private val startServerMethod by lazy { getHandlerClass().getMethod("startServer")!! }

  public fun startServer() {
    startServerMethod.invoke(null)
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")  // KTIJ-18982
  private val stopServerMethod by lazy { getHandlerClass().getMethod("stopServer", Int::class.java)!! }

  public fun stopServer(timeout: Int) {
    stopServerMethod.invoke(null, timeout)
  }


  @JvmStatic
  public fun main(args: Array<String>) {
    val classToLaunch = getNotNullProperty("org.jetbrains.projector.agent.classToLaunch")
    val agentJar = getNotNullProperty("org.jetbrains.projector.agent.path")

    attachAgent(agentJar)

    getMainMethodOf(classToLaunch).invoke(null, args)
  }
}
