/*
 * Copyright (c) 2019-2023, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

package org.jetbrains.projector.server.service

import org.jetbrains.projector.awt.image.PVolatileImage
import org.jetbrains.projector.awt.service.ImageCacher
import org.jetbrains.projector.common.protocol.data.ImageData
import org.jetbrains.projector.common.protocol.data.ImageId
import org.jetbrains.projector.common.protocol.toClient.ServerImageDataReplyEvent
import org.jetbrains.projector.server.ProjectorServer
import org.jetbrains.projector.server.core.util.SizeAware
import org.jetbrains.projector.util.loading.unprotect
import org.jetbrains.projector.util.logging.Logger
import sun.awt.image.SunVolatileImage
import sun.awt.image.ToolkitImage
import sun.java2d.StateTrackable
import java.awt.Image
import java.awt.image.*
import java.io.ByteArrayOutputStream
import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO

object ProjectorImageCacher : ImageCacher {

  override fun getImageId(image: Image, methodName: String): ImageId = when (image) {
    is BufferedImage -> putImage(image)

    is ToolkitImage -> getImageId(image.bufferedImage, "$methodName, extracted BufferedImage from ToolkitImage")

    is PVolatileImage -> ImageId.PVolatileImageId(image.id)

    is SunVolatileImage -> getImageId(image.snapshot, "$methodName, extracted snapshot from SunVolatileImage")

    is MultiResolutionImage -> image.resolutionVariants
                                 .singleOrNull()
                                 ?.let { getImageId(it, "$methodName, extracted single variant") }
                               ?: ImageId.Unknown(
                                 "$methodName received MultiResolutionImage with bad variant count (${image.resolutionVariants.size}): $image")

    else -> ImageId.Unknown("$methodName received ${image::class.qualifiedName}: $image")
  }

  val newImages by SizeAware(
    ConcurrentLinkedQueue<ServerImageDataReplyEvent>(),
    if (ProjectorServer.ENABLE_BIG_COLLECTIONS_CHECKS) ProjectorServer.BIG_COLLECTIONS_CHECKS_START_SIZE else null,
    Logger<ProjectorImageCacher>(),
  )

  private data class LivingImage(val reference: SoftReference<Image>, val data: ImageData)

  private data class IdentityImageId(val identityHash: Int, val stateHash: Int)

  private var idToImage = mutableMapOf<ImageId, LivingImage>()

  private val identityIdToImageId = mutableMapOf<IdentityImageId, ImageId>()

  private fun <T : Image> putImageIfNeeded(
    identityImageId: IdentityImageId,
    image: T,
    imageIdBuilder: T.() -> ImageId,
    imageConverter: T.() -> ImageData,
  ) {
    synchronized(this) {
      if (identityImageId !in identityIdToImageId) {
        val imageId = image.imageIdBuilder()

        identityIdToImageId[identityImageId] = imageId
        if (imageId !in idToImage) {
          val imageData = image.imageConverter()
          idToImage[imageId] = LivingImage(SoftReference(image), imageData)

          newImages.add(ServerImageDataReplyEvent(imageId, imageData))
        }
      }
    }
  }

  fun putImage(image: BufferedImage): ImageId {
    val id = IdentityImageId(
      identityHash = System.identityHashCode(image),
      stateHash = image.stateHash
    )

    putImageIfNeeded(id, image, BufferedImage::imageId, BufferedImage::toImageData)

    return identityIdToImageId[id]!!
  }

  fun getImage(id: ImageId): ImageData? {
    return idToImage[id]?.data
  }

  fun collectGarbage() {
    synchronized(this) {
      filterNullsOutOfMutableMap(idToImage)
      identityIdToImageId.removeAllImageIdsWithoutImages()
    }
  }

  private fun <K> isAlive(entry: Map.Entry<K, LivingImage>): Boolean {
    return entry.value.reference.get() != null
  }

  private fun <K> filterNullsOutOfMutableMap(map: MutableMap<K, LivingImage>) {
    val iterator = map.iterator()

    while (iterator.hasNext()) {
      val next = iterator.next()

      if (!isAlive(next)) {
        iterator.remove()
      }
    }
  }

  private fun <K> MutableMap<K, ImageId>.removeAllImageIdsWithoutImages() {
    val iterator = iterator()

    while (iterator.hasNext()) {
      val next = iterator.next()

      if (next.value !in idToImage) {
        iterator.remove()
      }
    }
  }
}

private fun BufferedImage.toPngBase64(): String {
  val imageInByte: ByteArray

  ByteArrayOutputStream().apply {
    ImageIO.write(this@toPngBase64, "png", this)
    this.flush()
    imageInByte = this.toByteArray()
    this.close()
  }

  val encoded = Base64.getEncoder().encode(imageInByte)

  return String(encoded)
}

fun BufferedImage.toImageData(): ImageData {
  return ImageData.PngBase64(this.toPngBase64())
}

private val dataFieldByte = DataBufferByte::class.java.getDeclaredField("data").apply {
  unprotect()
}

private val dataFieldInt = DataBufferInt::class.java.getDeclaredField("data").apply {
  unprotect()
}

val BufferedImage.imageId: ImageId
  get() = when (raster.dataBuffer) {
    is DataBufferByte -> {
      val pixels = dataFieldByte.get(raster.dataBuffer) as ByteArray

      ImageId.BufferedImageId(
        rasterDataBufferSize = pixels.size,
        contentHash = pixels.contentHashCode()
      )
    }
    is DataBufferInt -> {
      val pixels = dataFieldInt.get(raster.dataBuffer) as IntArray

      ImageId.BufferedImageId(
        rasterDataBufferSize = pixels.size,
        contentHash = pixels.contentHashCode()
      )
    }
    else -> error("Unsupported BufferedImage type")
  }

private val theTrackableField = DataBuffer::class.java.getDeclaredField("theTrackable").apply {
  unprotect()
}

val BufferedImage.stateHash
  get(): Int {
    val stateTrackable = theTrackableField.get(this.raster.dataBuffer) as StateTrackable
    val stateTracker = stateTrackable.stateTracker

    return System.identityHashCode(stateTracker)
  }
