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
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.peer.PComponentPeer
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.data.Point
import org.jetbrains.projector.common.protocol.toClient.ServerCaretInfoChangedEvent
import org.jetbrains.projector.common.protocol.toClient.data.idea.CaretInfo
import org.jetbrains.projector.server.core.ij.invokeWhenIdeaIsInitialized
import org.jetbrains.projector.server.platform.getTextAttributesCompat
import org.jetbrains.projector.server.platform.readAction
import org.jetbrains.projector.server.util.FontCacher
import org.jetbrains.projector.util.loading.UseProjectorLoader
import org.jetbrains.projector.util.logging.Logger
import sun.awt.AWTAccessor
import java.awt.Component
import java.awt.Font
import java.awt.peer.ComponentPeer
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

@UseProjectorLoader
class CaretInfoUpdater(private val onCaretInfoChanged: (ServerCaretInfoChangedEvent.CaretInfoChange) -> Unit) {

  private lateinit var thread: Thread

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

  private fun loadCaretInfo(): ServerCaretInfoChangedEvent.CaretInfoChange {

    val focusedEditor = getCurrentEditorImpl() ?: return ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets
    val focusedEditorComponent = focusedEditor.contentComponent

    if (!focusedEditorComponent.isShowing) return ServerCaretInfoChangedEvent.CaretInfoChange.NoCarets

    val componentLocation = focusedEditorComponent.locationOnScreen
    val scrollPane = focusedEditor.scrollPane
    val visibleEditorRect = scrollPane.viewport.viewRect

    val lineHeight = focusedEditor.lineHeight
    val lineAscent = focusedEditor.ascent

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

          CaretInfo(point)
        }

        val isVerticalScrollBarVisible = visibleEditorRect.height < focusedEditorComponent.height
        val verticalScrollBarWidth = if (isVerticalScrollBarVisible) scrollPane.verticalScrollBar?.width ?: 0 else 0

        val textColor = getTextColorBeforeCaret(focusedEditor)
        val editorFont = getFontBeforeCaret(focusedEditor)

        ServerCaretInfoChangedEvent.CaretInfoChange.Carets(
          points,
          fontId = FontCacher.getId(editorFont),
          fontSize = editorFont.size,
          editorWindowId = editorPWindow.id,
          editorMetrics = CommonRectangle(
            x = componentLocation.getX() - rootComponentLocation.x + visibleEditorRect.x,
            y = componentLocation.getY() - rootComponentLocation.y + visibleEditorRect.y,
            width = visibleEditorRect.width.toDouble(),
            height = visibleEditorRect.height.toDouble()
          ),
          lineHeight = lineHeight,
          lineAscent = lineAscent,
          verticalScrollBarWidth = verticalScrollBarWidth,
          textColor = textColor,
        )
      }
    }
  }

  private fun getTextColorBeforeCaret(editor: EditorEx): Int {
    val attrs = getTextAttributesBeforeCaret(editor) { it.foregroundColor != null }
    val color = attrs?.foregroundColor ?: editor.colorsScheme.defaultForeground
    return color.rgb
  }

  private fun getFontBeforeCaret(editor: EditorEx): Font {
    val attrs = getTextAttributesBeforeCaret(editor) { it.fontType != Font.PLAIN }

    val editorFontType = when (attrs?.fontType) {
      Font.BOLD -> EditorFontType.BOLD
      Font.ITALIC -> EditorFontType.ITALIC
      Font.BOLD or Font.ITALIC -> EditorFontType.BOLD_ITALIC
      else -> EditorFontType.PLAIN
    }

    return editor.colorsScheme.getFont(editorFontType)
  }

  private fun getTextAttributesBeforeCaret(editor: EditorEx, filter: (TextAttributes) -> Boolean): TextAttributes? {

    val caretOffset = readAction { editor.caretModel.offset }

    if (caretOffset <= 0) return null

    var bestFitAttributes: ExtendedTextAttributes? = null
    val compareAndUpdate = lambda@ { extendedTextAttributes: ExtendedTextAttributes ->
      if (!filter(extendedTextAttributes.attrs)) return@lambda

      bestFitAttributes = ExtendedTextAttributes.topLayeredAttributes(extendedTextAttributes, bestFitAttributes)
    }

    val highlightingProviders = listOf(::getAttrsFromRangeHighlighters, ::getAttrsFromHighlighterIterator)

    highlightingProviders.forEach {
      it(editor, caretOffset, compareAndUpdate)
    }

    return bestFitAttributes?.attrs
  }

  private fun getAttrsFromRangeHighlighters(
    editor: EditorEx,
    caretOffset: Int,
    compareAndUpdate: (ExtendedTextAttributes) -> Unit,
  ) {

    val rangeHighlighters = invokeAndWaitIfNeeded { editor.filteredDocumentMarkupModel.allHighlighters }

    val startPos = caretOffset - 1

    rangeHighlighters.forEach {
      val start = it.startOffset
      val end = it.endOffset

      if (startPos !in start until end) return@forEach

      val textAttrs = it.getTextAttributesCompat(editor.colorsScheme) ?: return@forEach

      compareAndUpdate(ExtendedTextAttributes(textAttrs, start until end, it.layer))
    }
  }

  private fun getAttrsFromHighlighterIterator(
    editor: EditorEx,
    caretOffset: Int,
    compareAndUpdate: (ExtendedTextAttributes) -> Unit,
  ) {

    val startPos = caretOffset - 1

    val highlightIterator = readAction { editor.highlighter.createIterator(startPos) }
    if (highlightIterator.atEnd()) return

    do {
      val candidateAttrs = highlightIterator.textAttributes
      val range = highlightIterator.start until highlightIterator.end
      compareAndUpdate(ExtendedTextAttributes(candidateAttrs, range, -1))

      highlightIterator.advance()
    } while (!highlightIterator.atEnd() && startPos in highlightIterator.start until highlightIterator.end)
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
