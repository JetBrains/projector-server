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
package org.jetbrains.projector.server.idea

import org.jetbrains.projector.server.log.Logger
import kotlin.concurrent.thread

private fun isIdeaInProperState(ideaClassLoader: ClassLoader?): Boolean {
  val loadingStateClass = Class.forName("com.intellij.diagnostic.LoadingState", false, ideaClassLoader)

  val loadingState = loadingStateClass
    .getDeclaredField("CONFIGURATION_STORE_INITIALIZED")
    .get(null)

  return loadingStateClass
    .getDeclaredMethod("isOccurred")
    .invoke(loadingState) as Boolean
}

fun invokeWhenIdeaIsInitialized(
  purpose: String,
  onNoIdeaFound: (() -> Unit)? = null,
  onInitialized: (ideaClassLoader: ClassLoader) -> Unit
) {
  thread(isDaemon = true) {
    if (onNoIdeaFound == null) {
      logger.debug { "Starting attempts to $purpose" }
    }

    while (true) {
      try {
        val ideaMainClassWithIdeaClassLoader = Class.forName("com.intellij.ide.WindowsCommandLineProcessor")
          .getDeclaredField("ourMainRunnerClass")
          .get(null) as Class<*>?

        if (ideaMainClassWithIdeaClassLoader != null) {  // null means we run with IDEA but it's not initialized yet
          val ideaClassLoader = ideaMainClassWithIdeaClassLoader.classLoader

          if (isIdeaInProperState(ideaClassLoader)) {
            onInitialized(ideaClassLoader)

            if (onNoIdeaFound == null) {
              logger.debug { "\"$purpose\" is done" }
            }
            break
          }
        }
      }
      catch (t: Throwable) {
        if (onNoIdeaFound == null) {
          logger.debug(t) { "Can't $purpose. It's OK if you don't run an IntelliJ platform based app." }
        }
        else {
          onNoIdeaFound()
        }
        break
      }

      Thread.sleep(1)
    }
  }
}

private val logger = Logger("IdeaStateKt")
