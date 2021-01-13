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

import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.data.PaintType
import org.jetbrains.projector.common.protocol.toClient.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformTest {

  @Test
  fun testExtractData() {
    listOf(
      listOf("a", "b", "a", "b", "c", "d"),
      listOf(),
      listOf(""),
      listOf("4")
    )
      .forEach { initialData ->
        val mutableCopy = initialData.toMutableList()

        val extracted = extractData(mutableCopy)

        assertEquals(initialData, extracted, "extracted data should match initial data")
        assertEquals(0, mutableCopy.size, "collection should be empty after data extraction")
      }
  }

  @Test
  fun testConvertToSimpleList() {
    // todo: move this trick somewhere near PGraphics2D but reuse in agent
    val identity = ServerSetTransformEvent(listOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))

    listOf(
      // intersecting:
      listOf(
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
        ),
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
        ),
      ),
      // moved to right:
      listOf(
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 110.0, 10.0, 800.0, 800.0),
        ),
        listOf(),
      ),
      // moved to bottom:
      listOf(
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 10.0, 110.0, 800.0, 800.0),
        ),
        listOf(),
      ),
      // intersecting:
      listOf(
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerSetFontEvent(0, 12),
          ServerDrawStringEvent("abc", 10.0, 10.0, 800.0),
        ),
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerSetFontEvent(0, 12),
          ServerDrawStringEvent("abc", 10.0, 10.0, 800.0),
        ),
      ),
      // moved to right:
      listOf(
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerSetFontEvent(0, 12),
          ServerDrawStringEvent("abc", 110.0, 10.0, 800.0),
        ),
        listOf(),
      ),
      // moved to bottom:
      listOf(
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerSetFontEvent(0, 12),
          ServerDrawStringEvent("abc", 10.0, 1110.0, 800.0),
        ),
        listOf(),
      ),
    ).forEach { (initial, expected) ->
      val actual = listOf(initial).convertToSimpleList()

      assertEquals(expected, actual, "bad conversion for initial list: $initial")
    }
  }
}
