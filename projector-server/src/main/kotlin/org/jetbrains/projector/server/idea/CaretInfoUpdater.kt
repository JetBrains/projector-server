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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.server.idea

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.EditorView
import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.peer.PComponentPeer
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.data.Point
import org.jetbrains.projector.common.protocol.toClient.ServerCaretInfoChangedEvent
import org.jetbrains.projector.common.protocol.toClient.data.idea.CaretInfo
import org.jetbrains.projector.server.core.ij.invokeWhenIdeaIsInitialized
import org.jetbrains.projector.server.util.FontCacher
import org.jetbrains.projector.util.loading.unprotect
import org.jetbrains.projector.util.logging.Logger
import sun.awt.AWTAccessor
import java.awt.Component
import java.awt.peer.ComponentPeer
import java.lang.reflect.Field
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

class CaretInfoUpdater(private val onCaretInfoChanged: (ServerCaretInfoChangedEvent.CaretInfoChange) -> Unit) {

  private lateinit var thread: Thread

  private val editorImplClass by lazy {
    EditorImpl::class.java
  }

  private val myViewField by lazy {
    editorImplClass
      .getDeclaredField("myView")
      .apply(Field::unprotect)
  }

  private val myDataManager by lazy { DataManager.getInstance() }

  private var errorOccurred = false

  private var lastCaretInfo: ServerCaretInfoChangedEvent.CaretInfoChange = ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets

  private fun updateCaretInfoIfNeeded(newCaretInfo: ServerCaretInfoChangedEvent.CaretInfoChange) {
    if (lastCaretInfo != newCaretInfo) {
      lastCaretInfo = newCaretInfo
      createCaretInfoEvent()
    }
  }

  fun createCaretInfoEvent() {
    onCaretInfoChanged(lastCaretInfo)
  }

  private fun getCurrentEditorImpl(): EditorImpl? {

    val dataContext = try {
      myDataManager.dataContextFromFocusAsync.blockingGet(DATA_CONTEXT_QUERYING_TIMEOUT_MS)
    } catch (e : TimeoutException) {
      null
    } ?: return null

    return dataContext.getData(CommonDataKeys.EDITOR) as? EditorImpl
  }

  // TODO Remove remaining reflection bits once we get rid of nominalLineHeight and plainSpaceWidth
  private fun loadCaretInfo(): ServerCaretInfoChangedEvent.CaretInfoChange {
    val editorFont = EditorUtil.getEditorFont()

    val focusedEditor = getCurrentEditorImpl() ?: return ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets
    val focusedEditorComponent = focusedEditor.contentComponent

    if (!focusedEditorComponent.isShowing) return ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets

    val componentLocation = focusedEditorComponent.locationOnScreen

    val focusedEditorView = myViewField.get(focusedEditor) as EditorView
    val nominalLineHeight = focusedEditorView.nominalLineHeight
    val plainSpaceWidth = focusedEditorView.plainSpaceWidth

    var rootComponent: Component? = focusedEditorComponent
    var editorPWindow: PWindow? = null

    while (rootComponent != null) {
      val peer = AWTAccessor.getComponentAccessor().getPeer<ComponentPeer>(rootComponent)

      if (peer is PComponentPeer) {
        editorPWindow = peer.pWindow
        break
      }

      rootComponent = rootComponent.parent
    }

    return when (editorPWindow) {
      null -> ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets

      else -> {
        val rootComponentLocation = rootComponent!!.locationOnScreen

        val editorLocationInWindowX = componentLocation.x - rootComponentLocation.x
        val editorLocationInWindowY = componentLocation.y - rootComponentLocation.y

        val points = focusedEditor.caretModel.allCarets.map {
          val caretLocationInEditor = invokeAndWaitIfNeeded { it.editor.visualPositionToXY(it.visualPosition) }

          val point = Point(
            x = (editorLocationInWindowX + caretLocationInEditor.x).toDouble(),
            y = (editorLocationInWindowY + caretLocationInEditor.y).toDouble(),
          )

          CaretInfo(point, 0, point)
        }

        ServerCaretInfoChangedEvent.CaretInfoChange.Carets(
          points,
          fontId = FontCacher.getId(editorFont),
          fontSize = editorFont.size,
          nominalLineHeight = nominalLineHeight,
          plainSpaceWidth = plainSpaceWidth,
          editorWindowId = editorPWindow.id,
          editorMetrics = CommonRectangle(
            x = componentLocation.getX(),
            y = componentLocation.getY(),
            width = focusedEditorComponent.width.toDouble(),
            height = focusedEditorComponent.height.toDouble()
          )
        )
      }
    }
  }

  fun start() {
    invokeWhenIdeaIsInitialized("search for editors") {
      thread = thread(isDaemon = true) {
        while (!Thread.currentThread().isInterrupted) {
          try {
            try {
              val newCaretInfo = loadCaretInfo()
              updateCaretInfoIfNeeded(newCaretInfo)
            }
            catch (npe: NullPointerException) {
              updateCaretInfoIfNeeded(ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets)
            }
            catch (t: Throwable) {
              if (!errorOccurred) {
                logger.info(t) { "Can't get caret info" }
                errorOccurred = true
              }
            }

            Thread.sleep(10)
          }
          catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
          }
        }
      }
    }
  }

  fun stop() {
    if (this::thread.isInitialized) {
      thread.interrupt()
    }
  }

  companion object {

    private val logger = Logger<CaretInfoUpdater>()

    private const val DATA_CONTEXT_QUERYING_TIMEOUT_MS = 1000
  }
}
