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
package org.jetbrains.projector.agent

import com.sun.tools.attach.VirtualMachine
import java.lang.management.ManagementFactory
import java.lang.reflect.Method

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

  @JvmStatic
  public fun main(args: Array<String>) {
    val classToLaunch = getNotNullProperty("org.jetbrains.projector.agent.classToLaunch")
    val agentJar = getNotNullProperty("org.jetbrains.projector.agent.path")

    attachAgent(agentJar)

    getMainMethodOf(classToLaunch).invoke(null, args)
  }
}
