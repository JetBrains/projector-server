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
package org.jetbrains.projector.server

import java.lang.reflect.Method

object ProjectorLauncher {

  /* Field is public for the case, when someone would like to launch server from their own code. */
  @Suppress("Could be private")
  const val MAIN_CLASS_PROPERTY_NAME = "org.jetbrains.projector.server.classToLaunch"

  @JvmStatic
  fun main(args: Array<String>) {
    val canonicalMainClassName = requireNotNull(System.getProperty(MAIN_CLASS_PROPERTY_NAME)) {
      "System property `$MAIN_CLASS_PROPERTY_NAME` isn't assigned, so can't understand which class to launch"
    }
    val mainMethod = getMainMethodOf(canonicalMainClassName)

    runProjectorServer()

    mainMethod.invoke(null, args)
  }

  private fun getMainMethodOf(canonicalClassName: String): Method {
    val mainClass = Class.forName(canonicalClassName)
    return mainClass.getMethod("main", Array<String>::class.java)
  }

  private fun runProjectorServer() {
    System.setProperty(ProjectorServer.ENABLE_PROPERTY_NAME, true.toString())

    assert(ProjectorServer.isEnabled) { "Can't start the ${ProjectorServer::class.simpleName} because it's disabled..." }

    val server = ProjectorServer.startServer()

    Runtime.getRuntime().addShutdownHook(object : Thread() {

      override fun run() {
        server.stop()
      }
    })
  }
}
