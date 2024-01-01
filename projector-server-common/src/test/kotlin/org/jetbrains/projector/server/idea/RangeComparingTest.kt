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
package org.jetbrains.projector.server.idea

import com.intellij.openapi.editor.markup.TextAttributes
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class RangeComparingTest {

  @Test
  fun testBothNull() {
    val result = ExtendedTextAttributes.topLayeredAttributes(null, null)
    assertNull(result)
  }

  @Test
  fun testFirstNull() {
    val second = ExtendedTextAttributes(TextAttributes(), 0..100, 200)
    val result = ExtendedTextAttributes.topLayeredAttributes(null, second)
    assertSame(second, result)
  }

  @Test
  fun testSecondNull() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 200)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, null)
    assertSame(first, result)
  }

  @Test
  fun testOneInsideOther() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 200)
    val second = ExtendedTextAttributes(TextAttributes(), 2..20, 200)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, second)
    assertSame(second, result)
  }

  @Test
  fun testOneInsideOtherDifferentPriorities() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 200)
    val second = ExtendedTextAttributes(TextAttributes(), 2..20, 100)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, second)
    assertSame(first, result)
  }

  @Test
  fun testSameStartOffset() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 100)
    val second = ExtendedTextAttributes(TextAttributes(), 0..400, 100)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, second)
    assertSame(first, result)
  }

  @Test
  fun testSameEndOffset() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 100)
    val second = ExtendedTextAttributes(TextAttributes(), 40..100, 100)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, second)
    assertSame(second, result)
  }

  @Test
  fun testSameRange() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 200)
    val second = ExtendedTextAttributes(TextAttributes(), 0..100, 100)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, second)
    assertSame(first, result)
  }

  @Test
  fun testSameRangeAndPriority() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 100)
    val second = ExtendedTextAttributes(TextAttributes(), 0..100, 100)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, second)
    assertSame(first, result)
  }

  @Test
  fun testOverlapping() {
    val first = ExtendedTextAttributes(TextAttributes(), 0..100, 100)
    val second = ExtendedTextAttributes(TextAttributes(), 80..120, 100)
    val result = ExtendedTextAttributes.topLayeredAttributes(first, second)
    assertSame(first, result)
  }

  @Test
  fun testNonOverlapping() {
    val first = ExtendedTextAttributes(TextAttributes(), 50..100, 100)
    val second = ExtendedTextAttributes(TextAttributes(), 5..10, 100)

    assertFailsWith<IllegalArgumentException> { ExtendedTextAttributes.topLayeredAttributes(first, second) }
  }
}
