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
import org.jetbrains.projector.agent.GraphicsTransformer.Companion.DRAW_HANDLER_PACKAGE
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.util.*

public object AgentLauncher {
  private var getClientListMethod: Method? = null
  private var disconnectAllMethod: Method? = null
  private var disconnectByIpMethod: Method? = null
  private var addClientsObserverMethod: Method? = null
  private var removeClientsObserverMethod: Method? = null

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

  private fun getHandlerClass(): Class<*> {
    return ClassLoader.getSystemClassLoader().loadClass("$DRAW_HANDLER_PACKAGE")
  }

  @JvmStatic
  public fun getClientList(): Array<Array<String?>> {
    if (getClientListMethod == null) {
      getClientListMethod = getHandlerClass().getMethod("getClientList")
    }
    val result = getClientListMethod?.invoke(null) ?: emptyArray<Array<String?>>()
    @Suppress("UNCHECKED_CAST")
    return result as Array<Array<String?>>
  }

  @JvmStatic
  public fun disconnectAll() {
    if (disconnectAllMethod == null) {
      disconnectAllMethod = getHandlerClass().getMethod("disconnectAll")
    }
    disconnectAllMethod?.invoke(null)
  }

  @JvmStatic
  public fun disconnectByIp(ip: String) {
    if (disconnectByIpMethod == null) {
      disconnectByIpMethod = getHandlerClass().getMethod("disconnectByIp", String::class.java)
    }
    disconnectByIpMethod?.invoke(null, ip)
  }

  @JvmStatic
  public fun addClientsObserver(observer: Observer) {
    if (addClientsObserverMethod == null) {
      addClientsObserverMethod = getHandlerClass().getMethod("addClientsObserver", Object::class.java)
    }

    addClientsObserverMethod?.invoke(null, observer)
  }

  @JvmStatic
  public fun removeClientsObserver(observer: Observer) {
    if (removeClientsObserverMethod == null) {
      removeClientsObserverMethod = getHandlerClass().getMethod("removeClientsObserver", Object::class.java)
    }
    removeClientsObserverMethod?.invoke(null, observer)
  }

  @JvmStatic
  public fun main(args: Array<String>) {
    val classToLaunch = getNotNullProperty("org.jetbrains.projector.agent.classToLaunch")
    val agentJar = getNotNullProperty("org.jetbrains.projector.agent.path")

    attachAgent(agentJar)

    getMainMethodOf(classToLaunch).invoke(null, args)
  }
}
