/*
 * Copyright (c) 2019-2024, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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
package org.jetbrains.projector.server.util

import org.jetbrains.projector.common.protocol.data.CommonRectangle
import org.jetbrains.projector.common.protocol.data.PaintType
import org.jetbrains.projector.common.protocol.toClient.*
import org.jetbrains.projector.server.core.convert.toClient.extractData
import org.jetbrains.projector.server.core.convert.toClient.shrinkEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformTest {

  private data class InitialExpected<I, E>(val testName: String, val initial: I, val expected: E)

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
  fun testConvertToSimpleListAndRemoveDuplicates() {
    val identity = ServerSetTransformEvent(listOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))

    listOf(
      InitialExpected(
        "no overrides should be taken",
        listOf(
          listOf(
            ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
            identity,
            ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
          ),
        ),
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
        ),
      ),
      InitialExpected(
        "same overrides should be just skipped",
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
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
          ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
        ),
      ),
      InitialExpected(
        "changed overrides should be taken",
        listOf(
          listOf(
            ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
            identity,
            ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
          ),
          listOf(
            ServerSetClipEvent(CommonRectangle(1.0, 0.0, 100.0, 100.0)),
            ServerSetTransformEvent(listOf(2.0, 0.0, 0.0, 1.0, 0.0, 0.0)),
            ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
          ),
        ),
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
          ServerSetClipEvent(CommonRectangle(1.0, 0.0, 100.0, 100.0)),
          ServerSetTransformEvent(listOf(2.0, 0.0, 0.0, 1.0, 0.0, 0.0)),
          ServerPaintRectEvent(PaintType.FILL, 10.0, 10.0, 800.0, 800.0),
        ),
      ),
    ).forEach { (testName, initial, expected) ->
      val actual = initial.map { ServerDrawCommandsEvent.Target.Onscreen(1) to it }.shrinkEvents()

      assertEquals(expected, actual.singleOrNull()?.drawEvents, "bad conversion for initial list ($testName): $initial")
    }
  }

  @Test
  fun testConvertToSimpleListAndRemoveInvisible() {
    // todo: move this trick somewhere near PGraphics2D but reuse in agent
    val identity = ServerSetTransformEvent(listOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))

    listOf(
      InitialExpected(
        "intersecting clip and paint",
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
      InitialExpected(
        "paint moved out to the right should be skipped",
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 110.0, 10.0, 800.0, 800.0),
        ),
        listOf(),
      ),
      InitialExpected(
        "paint moved out to the bottom should be skipped",
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerPaintRectEvent(PaintType.FILL, 10.0, 110.0, 800.0, 800.0),
        ),
        listOf(),
      ),
      InitialExpected(
        "intersecting clip and string",
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
      InitialExpected(
        "string moved out to the right should be skipped",
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerSetFontEvent(0, 12),
          ServerDrawStringEvent("abc", 110.0, 10.0, 800.0),
        ),
        listOf(),
      ),
      InitialExpected(
        "string moved out to the bottom should be skipped",
        listOf(
          ServerSetClipEvent(CommonRectangle(0.0, 0.0, 100.0, 100.0)),
          identity,
          ServerSetFontEvent(0, 12),
          ServerDrawStringEvent("abc", 10.0, 1110.0, 800.0),
        ),
        listOf(),
      ),
    ).forEach { (testName, initial, expected) ->
      val shrunk = listOf(ServerDrawCommandsEvent.Target.Onscreen(1) to initial).shrinkEvents()
      val actual = when {
        shrunk.isEmpty() -> emptyList()
        else -> shrunk.singleOrNull()?.drawEvents
      }

      assertEquals(expected, actual, "bad conversion for initial list ($testName): $initial")
    }
  }
}
