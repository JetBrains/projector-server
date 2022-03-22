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

package org.jetbrains.projector.server

import org.jetbrains.projector.awt.PToolkit
import org.jetbrains.projector.awt.font.PFontManager
import org.jetbrains.projector.awt.image.PGraphicsEnvironment
import org.jetbrains.projector.awt.peer.PWindowPeer
import org.jetbrains.projector.awt.service.WindowSystemHelper
import org.jetbrains.projector.server.ProjectorServer.Companion.ENABLE_PROPERTY_NAME
import org.jetbrains.projector.util.loading.unprotect
import sun.awt.AWTAccessor
import sun.font.FontManagerFactory
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window
import java.awt.peer.ComponentPeer
import javax.swing.SwingUtilities

internal fun setupGraphicsEnvironment() {
  val classes = GraphicsEnvironment::class.java.declaredClasses
  val localGE = classes.single()
  check(localGE.name == "java.awt.GraphicsEnvironment\$LocalGE")

  localGE.getDeclaredField("INSTANCE").apply {
    unprotect()

    set(null, PGraphicsEnvironment())
  }
}

internal fun setupToolkit() {
  Toolkit::class.java.getDeclaredField("toolkit").apply {
    unprotect()

    set(null, PToolkit())
  }
}

internal fun setupFontManager() {
  FontManagerFactory::class.java.getDeclaredField("instance").apply {
    unprotect()

    set(null, PFontManager)
  }
}

internal fun setupWindowHelper() {
  WindowSystemHelper.instance = object : WindowSystemHelper {

    override fun getParentWindow(component: Component) = when (component) {
      is Window -> component.owner
      else -> SwingUtilities.getWindowAncestor(component)
    }?.let { (AWTAccessor.getComponentAccessor().getPeer<ComponentPeer>(it) as? PWindowPeer)?.pWindow }
  }
}

internal fun setupRepaintManager() {
  // todo: when we do smth w/ RepaintManager, IDEA crashes.
  //       Maybe it's because AppContext is used.
  //       Disable repaint manager setup for now
  //val repaintManagerKey = RepaintManager::class.java
  //val appContext = AppContext.getAppContext()
  //appContext.put(repaintManagerKey, HeadlessRepaintManager())

  //RepaintManager.currentManager(null).isDoubleBufferingEnabled = false
}

internal fun setupSystemProperties() {
  // Setting these properties as run arguments isn't enough because they can be overwritten by JVM
  System.setProperty(ENABLE_PROPERTY_NAME, true.toString())
  System.setProperty("java.awt.graphicsenv", PGraphicsEnvironment::class.java.canonicalName)
  System.setProperty("awt.toolkit", PToolkit::class.java.canonicalName)
  System.setProperty("sun.font.fontmanager", PFontManager::class.java.canonicalName)
  System.setProperty("java.awt.headless", false.toString())
  System.setProperty("swing.bufferPerWindow", false.toString())
  System.setProperty("awt.nativeDoubleBuffering", true.toString())  // enable "native" double buffering to disable db in Swing
  System.setProperty("swing.volatileImageBufferEnabled", false.toString())
  System.setProperty("keymap.current.os.only", false.toString())
}
