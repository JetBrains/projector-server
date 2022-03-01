/*
 * Copyright (c) 2019-2022, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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
package org.jetbrains.projector.server.cache

import org.jetbrains.projector.server.service.ProjectorImageCacher
import org.jetbrains.projector.server.service.toImageData
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// todo: add tests for garbage collection
class ImageCacherTest {

  @Test
  fun `get image should return previously put image`() {
    val bi = BufferedImage(123, 456, BufferedImage.TYPE_INT_ARGB)
    val imageId = ProjectorImageCacher.putImage(bi)
    val returnedImageData = ProjectorImageCacher.getImage(imageId)
    assertEquals(bi.toImageData(), returnedImageData)
  }

  @Test
  fun `images with different size should have different IDs`() {
    val bi1 = BufferedImage(123, 456, BufferedImage.TYPE_INT_ARGB)
    val imageId1 = ProjectorImageCacher.putImage(bi1)
    val bi2 = BufferedImage(123, 4, BufferedImage.TYPE_INT_ARGB)
    val imageId2 = ProjectorImageCacher.putImage(bi2)
    assertNotEquals(imageId1, imageId2)
  }

  @Test
  fun `images with different contents should have different IDs`() {
    val bi1 = BufferedImage(123, 456, BufferedImage.TYPE_INT_ARGB)
    val imageId1 = ProjectorImageCacher.putImage(bi1)
    val bi2 = BufferedImage(123, 456, BufferedImage.TYPE_INT_ARGB)
    val g2 = bi2.createGraphics()
    g2.color = Color.YELLOW
    g2.fillRect(20, 3, 4, 5)
    val imageId2 = ProjectorImageCacher.putImage(bi2)
    assertNotEquals(imageId1, imageId2)
  }

  @Test
  fun `changing image graphics state should not change image ID`() {
    val bi = BufferedImage(123, 456, BufferedImage.TYPE_INT_ARGB)
    val imageId = ProjectorImageCacher.putImage(bi)

    val graphics = bi.createGraphics()
    graphics.scale(2.0, 2.0)
    val scaledImageId = ProjectorImageCacher.putImage(bi)
    assertEquals(imageId, scaledImageId)
  }

  @Test
  fun `changing image contents should change image ID and data`() {
    val bi = BufferedImage(123, 456, BufferedImage.TYPE_INT_ARGB)
    val imageId = ProjectorImageCacher.putImage(bi)
    val initialImageData = ProjectorImageCacher.getImage(imageId)
    check(bi.toImageData() == initialImageData) { "Should be checked in another test" }

    // change for the first time should change ID and data:

    val graphics = bi.createGraphics()
    graphics.color = Color.CYAN
    graphics.fillRect(1, 2, 3, 4)
    val changedImageId = ProjectorImageCacher.putImage(bi)
    assertNotEquals(imageId, changedImageId)
    val changedImageData = ProjectorImageCacher.getImage(changedImageId)
    assertEquals(bi.toImageData(), changedImageData)
    assertNotEquals(initialImageData, changedImageData)

    // change for the second time should change ID and data too:

    graphics.color = Color.BLACK
    graphics.fillRect(10, 2, 3, 40)
    val changedImageId2 = ProjectorImageCacher.putImage(bi)
    assertNotEquals(imageId, changedImageId2)
    assertNotEquals(changedImageId, changedImageId2)
    val changedImageData2 = ProjectorImageCacher.getImage(changedImageId2)
    assertEquals(bi.toImageData(), changedImageData2)
    assertNotEquals(initialImageData, changedImageData2)
    assertNotEquals(changedImageData, changedImageData2)
  }
}
