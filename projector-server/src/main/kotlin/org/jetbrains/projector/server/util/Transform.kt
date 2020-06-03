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
package org.jetbrains.projector.server.util

import org.jetbrains.projector.common.misc.Do
import org.jetbrains.projector.common.protocol.toClient.*

fun <E> extractData(iterable: MutableIterable<E>): List<E> {
  val answer = mutableListOf<E>()

  iterable.removeAll(answer::add)

  return answer
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
    packedEvents.forEach { event ->
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

        is ServerWindowPaintEvent -> answer.add(event)
      }
    }
  }

  return answer
}
