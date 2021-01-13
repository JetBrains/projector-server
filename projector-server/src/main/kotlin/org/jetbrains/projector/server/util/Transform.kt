/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2021 JetBrains s.r.o.
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
package org.jetbrains.projector.server.util

import org.jetbrains.projector.common.misc.Do
import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.toClient.*
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D

fun <E> extractData(iterable: MutableIterable<E>): List<E> {
  val answer = mutableListOf<E>()

  iterable.removeAll(answer::add)

  return answer
}

private fun Rectangle2D.isVisible(clip: ServerSetClipEvent, tx: ServerSetTransformEvent): Boolean {
  val clipRect = clip.shape as? CommonRectangle ?: return true  // can't tell
  val identityTransformStrBounds = AffineTransform(tx.tx.toDoubleArray()).createTransformedShape(this)

  return identityTransformStrBounds.intersects(clipRect.x, clipRect.y, clipRect.width, clipRect.height)
}

private fun ServerDrawStringEvent.isVisible(font: ServerSetFontEvent?, clip: ServerSetClipEvent?, tx: ServerSetTransformEvent?): Boolean {
  if (font == null || clip == null || tx == null) {
    return true  // can't tell
  }

  val height = font.fontSize * 3  // todo: it's rough rounding up

  val strBounds = Rectangle2D.Double(
    this.x,
    this.y - height,
    this.desiredWidth,
    2.0 * height,
  )

  return strBounds.isVisible(clip, tx)
}

private fun ServerPaintRectEvent.isVisible(clip: ServerSetClipEvent?, tx: ServerSetTransformEvent?): Boolean {
  if (clip == null || tx == null) {
    return true  // can't tell
  }

  val strBounds = Rectangle2D.Double(
    this.x,
    this.y,
    this.width,
    this.height,
  )

  return strBounds.isVisible(clip, tx)
}

fun List<List<ServerWindowEvent>>.convertToSimpleList(): List<ServerWindowEvent> {
  val answer = mutableListOf<ServerWindowEvent>()

  var lastCompositeEvent: ServerSetCompositeEvent? = null
  var lastClipEvent: ServerSetClipEvent? = null
  var lastTransformEvent: ServerSetTransformEvent? = null
  var lastFontEvent: ServerSetFontEvent? = null
  var lastSetPaintEvent: ServerSetPaintEvent? = null
  var lastStrokeEvent: ServerSetStrokeEvent? = null

  this.forEach { packedEvents ->
    packedEvents.forEach innerLoop@{ event ->
      Do exhaustive when (event) {
        is ServerWindowStateEvent -> Do exhaustive when (event) {
          is ServerSetCompositeEvent -> if (event == lastCompositeEvent) {
            Unit
          }
          else {
            lastCompositeEvent = event
            answer.add(event)
          }

          is ServerSetClipEvent -> if (event == lastClipEvent) {
            Unit
          }
          else {
            lastClipEvent = event
            answer.add(event)
          }

          is ServerSetTransformEvent -> if (event == lastTransformEvent) {
            Unit
          }
          else {
            lastTransformEvent = event
            answer.add(event)
          }

          is ServerSetPaintEvent -> if (event == lastSetPaintEvent) {
            Unit
          }
          else {
            lastSetPaintEvent = event
            answer.add(event)
          }

          is ServerSetFontEvent -> if (event == lastFontEvent) {
            Unit
          }
          else {
            lastFontEvent = event
            answer.add(event)
          }

          is ServerSetStrokeEvent -> if (event == lastStrokeEvent) {
            Unit
          }
          else {
            lastStrokeEvent = event
            answer.add(event)
          }

          is ServerWindowToDoStateEvent -> answer.add(event)
        }

        is ServerWindowPaintEvent -> {
          val visible = when (event) {
            is ServerDrawStringEvent -> event.isVisible(lastFontEvent, lastClipEvent, lastTransformEvent)
            is ServerPaintRectEvent -> event.isVisible(lastClipEvent, lastTransformEvent)
            else -> true
          }

          if (visible) {
            answer.add(event)
          }
          else {
            Unit
          }
        }
      }
    }
  }

  while (answer.isNotEmpty() && answer.last() is ServerWindowStateEvent) {
    answer.removeLast()
  }

  return answer
}
