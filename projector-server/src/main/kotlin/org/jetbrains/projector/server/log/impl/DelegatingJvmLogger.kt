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
package org.jetbrains.projector.server.log.impl

import org.jetbrains.projector.common.misc.Do
import org.jetbrains.projector.server.core.ij.invokeWhenIdeaIsInitialized
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class DelegatingJvmLogger(tag: String) : JvmLogger {

  private var ideaLoggerState: IdeaLoggerState = IdeaLoggerState.WaitingIdeaLoggerState()

  private val ideaLoggerStateLock = ReentrantReadWriteLock(true)

  private val consoleJvmLogger = ConsoleJvmLogger(tag)

  init {
    invokeWhenIdeaIsInitialized(
      "get Idea Logger",
      onNoIdeaFound = fun() {
        ideaLoggerStateLock.write {
          ideaLoggerState = IdeaLoggerState.NoIdea
        }
      },
      onInitialized = fun(ideaClassLoader: ClassLoader) {
        ideaLoggerStateLock.write {
          val notLoggedEvents = (ideaLoggerState as IdeaLoggerState.WaitingIdeaLoggerState).notLoggedEvents

          val initializedIdeaLoggerState = IdeaLoggerState.InitializedIdeaLoggerState(ideaClassLoader, tag)

          notLoggedEvents.forEach {
            Do exhaustive when (it) {
              is LoggingEvent.Error -> initializedIdeaLoggerState.ideaJvmLogger.error(it.t, it.lazyMessage)
              is LoggingEvent.Info -> initializedIdeaLoggerState.ideaJvmLogger.info(it.t, it.lazyMessage)
              is LoggingEvent.Debug -> initializedIdeaLoggerState.ideaJvmLogger.debug(it.t, it.lazyMessage)
            }
          }

          ideaLoggerState = initializedIdeaLoggerState
        }
      }
    )
  }

  override fun error(t: Throwable?, lazyMessage: () -> String): Unit = ideaLoggerStateLock.read {
    log(
      consoleJvmLogger = consoleJvmLogger,
      ideaLoggerState = ideaLoggerState,
      logFunction = JvmLogger::error,
      loggingEventConstructor = LoggingEvent::Error,
      t = t,
      lazyMessage = lazyMessage
    )
  }

  override fun info(t: Throwable?, lazyMessage: () -> String): Unit = ideaLoggerStateLock.read {
    log(
      consoleJvmLogger = consoleJvmLogger,
      ideaLoggerState = ideaLoggerState,
      logFunction = JvmLogger::info,
      loggingEventConstructor = LoggingEvent::Info,
      t = t,
      lazyMessage = lazyMessage
    )
  }

  override fun debug(t: Throwable?, lazyMessage: () -> String): Unit = ideaLoggerStateLock.read {
    log(
      consoleJvmLogger = consoleJvmLogger,
      ideaLoggerState = ideaLoggerState,
      logFunction = JvmLogger::debug,
      loggingEventConstructor = LoggingEvent::Debug,
      t = t,
      lazyMessage = lazyMessage
    )
  }

  companion object {

    private fun log(
      consoleJvmLogger: ConsoleJvmLogger,
      ideaLoggerState: IdeaLoggerState,
      logFunction: JvmLogger.(t: Throwable?, lazyMessage: () -> String) -> Unit,
      loggingEventConstructor: (t: Throwable?, lazyMessage: () -> String) -> LoggingEvent,
      t: Throwable?,
      lazyMessage: () -> String,
    ) {
      consoleJvmLogger.logFunction(t, lazyMessage)

      Do exhaustive when (ideaLoggerState) {
        is IdeaLoggerState.WaitingIdeaLoggerState -> ideaLoggerState.notLoggedEvents.add(loggingEventConstructor(t, lazyMessage))

        is IdeaLoggerState.InitializedIdeaLoggerState -> ideaLoggerState.ideaJvmLogger.logFunction(t, lazyMessage)

        is IdeaLoggerState.NoIdea -> Unit
      }
    }

    private sealed class LoggingEvent(val t: Throwable?, val lazyMessage: () -> String) {

      class Error(t: Throwable?, lazyMessage: () -> String) : LoggingEvent(t, lazyMessage)

      class Info(t: Throwable?, lazyMessage: () -> String) : LoggingEvent(t, lazyMessage)

      class Debug(t: Throwable?, lazyMessage: () -> String) : LoggingEvent(t, lazyMessage)
    }

    private sealed class IdeaLoggerState {

      class WaitingIdeaLoggerState : IdeaLoggerState() {

        val notLoggedEvents: Queue<LoggingEvent> = ArrayDeque<LoggingEvent>()
      }

      class InitializedIdeaLoggerState(ideaClassLoader: ClassLoader, tag: String) : IdeaLoggerState() {

        val ideaJvmLogger: JvmLogger = IdeaJvmLogger(ideaClassLoader, tag)
      }

      object NoIdea : IdeaLoggerState()
    }
  }
}
