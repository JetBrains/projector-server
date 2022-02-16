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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt.image

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.font.PFontManager
import sun.awt.image.BufImgSurfaceData
import sun.java2d.SunGraphics2D
import sun.java2d.SunGraphicsEnvironment
import java.awt.*
import java.awt.image.BufferedImage
import java.util.*

class PGraphicsEnvironment private constructor() : SunGraphicsEnvironment() {

  companion object {
    var clientDoesWindowManagement: Boolean = false
    val devices = ArrayList<PGraphicsDevice>().apply { add(PGraphicsDevice("Screen0")) }

    val defaultDevice: PGraphicsDevice
      get() = devices[0]

    fun setupDisplays(additionalDisplays: List<Pair<Rectangle, Double>>) {
      devices.clear()
      additionalDisplays.forEachIndexed { i, it ->
        val element = PGraphicsDevice("Screen$i")
        element.bounds.bounds = it.first
        element.scaleFactor = it.second
        devices.add(element)
      }
      fireDisplaysChanged()
    }

    private fun fireDisplaysChanged() {
      instance.displayChanged()
      PWindow.windows.forEach { it.updateGraphics() }
    }

    @Suppress("RedundantVisibilityModifier") // used in IjAwtTransformer
    @JvmStatic
    public val instance = PGraphicsEnvironment()
  }

  val xResolution: Double = 96.0
  val yResolution: Double = 96.0

  override fun getScreenDevices(): Array<GraphicsDevice> {
    return devices.toTypedArray()
  }

  override fun getDefaultScreenDevice(): GraphicsDevice {
    return defaultDevice
  }

  override fun createGraphics(img: BufferedImage): Graphics2D {
    // TODO: will it work without hardware acceleration?
    return SunGraphics2D(BufImgSurfaceData.createData(img), Color.WHITE, Color.WHITE, PFontManager.allInstalledFonts.first())
  }

  override fun getAllFonts(): Array<Font> {
    return PFontManager.allInstalledFonts
  }

  override fun getAvailableFontFamilyNames(): Array<String> {
    return getAvailableFontFamilyNames(Locale.getDefault())
  }

  override fun getAvailableFontFamilyNames(requestedLocale: Locale): Array<String> {
    return PFontManager.getInstalledFontFamilyNames(requestedLocale)
  }

  fun setDefaultDeviceSize(width: Int, height: Int) {
    defaultDevice.bounds.setSize(width, height)
    fireDisplaysChanged()
  }

  override fun getNumScreens(): Int = devices.size

  override fun isDisplayLocal(): Boolean = false

  override fun makeScreenDevice(p0: Int): GraphicsDevice = devices[p0]
}
