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
import java.awt.Font
import java.awt.geom.Point2D
import java.awt.peer.ComponentPeer
import java.lang.reflect.Field
import javax.swing.text.JTextComponent
import kotlin.concurrent.thread

class CaretInfoUpdater(private val onCaretInfoChanged: (ServerCaretInfoChangedEvent.CaretInfoChange) -> Unit) {

  private lateinit var thread: Thread

  private lateinit var ideaClassLoader: ClassLoader

  private val editorImplClass by lazy {
    Class.forName("com.intellij.openapi.editor.impl.EditorImpl", false, ideaClassLoader)
  }

  private val ourCaretBlinkingCommandField by lazy {
    editorImplClass
      .getDeclaredField("ourCaretBlinkingCommand")
      .apply(Field::unprotect)
  }

  private val myEditorField by lazy {
    Class
      .forName("com.intellij.openapi.editor.impl.EditorImpl\$RepaintCursorCommand", false, ideaClassLoader)
      .getDeclaredField("myEditor")
      .apply(Field::unprotect)
  }

  private val myEditorComponentField by lazy {
    editorImplClass
      .getDeclaredField("myEditorComponent")
      .apply(Field::unprotect)
  }

  private val myCaretCursorField by lazy {
    editorImplClass
      .getDeclaredField("myCaretCursor")
      .apply(Field::unprotect)
  }

  private val myLocationsField by lazy {
    Class
      .forName("com.intellij.openapi.editor.impl.EditorImpl\$CaretCursor", false, ideaClassLoader)
      .getDeclaredField("myLocations")
      .apply(Field::unprotect)
  }

  private val myPointField by lazy {
    Class
      .forName("com.intellij.openapi.editor.impl.EditorImpl\$CaretRectangle", false, ideaClassLoader)
      .getDeclaredField("myPoint")
  }

  private val getEditorFontMethod by lazy {
    Class
      .forName("com.intellij.openapi.editor.ex.util.EditorUtil", false, ideaClassLoader)
      .getDeclaredMethod("getEditorFont")
  }

  private val myViewField by lazy {
    editorImplClass
      .getDeclaredField("myView")
      .apply(Field::unprotect)
  }

  private val editorViewClass by lazy {
    Class.forName("com.intellij.openapi.editor.impl.view.EditorView", false, ideaClassLoader)
  }

  private val getNominalLineHeightMethod by lazy {
    editorViewClass.getDeclaredMethod("getNominalLineHeight")
  }

  private val getPlainSpaceWidthMethod by lazy {
    editorViewClass.getDeclaredMethod("getPlainSpaceWidth")
  }

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

  private fun loadCaretInfo(): ServerCaretInfoChangedEvent.CaretInfoChange {
    val editorFont = getEditorFontMethod.invoke(null) as Font

    val focusedCaretBlinkingCommand = ourCaretBlinkingCommandField.get(null)
    val focusedEditor = myEditorField.get(focusedCaretBlinkingCommand)
    val focusedEditorComponent = myEditorComponentField.get(focusedEditor) as JTextComponent

    if (!focusedEditorComponent.isShowing) return ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets

    val componentLocation = focusedEditorComponent.locationOnScreen

    val focusedEditorView = myViewField.get(focusedEditor)
    val nominalLineHeight = getNominalLineHeightMethod.invoke(focusedEditorView) as Int
    val plainSpaceWidth = getPlainSpaceWidthMethod.invoke(focusedEditorView) as Float

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

        val focusedCaretCursor = myCaretCursorField.get(focusedEditor)
        val focusedLocations = myLocationsField.get(focusedCaretCursor) as Array<*>
        val points = focusedLocations
          .filterNotNull()
          .map(myPointField::get)
          .map { it as Point2D }
          .map {
            CaretInfo(
              Point(
                x = componentLocation.x - rootComponentLocation.x + it.x,
                y = componentLocation.y - rootComponentLocation.y + it.y
              )
            )
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
    invokeWhenIdeaIsInitialized("search for editors") { ideaClassLoader ->
      this.ideaClassLoader = ideaClassLoader

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
  }
}
