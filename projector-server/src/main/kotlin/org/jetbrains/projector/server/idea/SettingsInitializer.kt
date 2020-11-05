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
package org.jetbrains.projector.server.idea

import org.jetbrains.projector.awt.image.PGraphics2D
import org.jetbrains.projector.server.core.ij.invokeWhenIdeaIsInitialized
import org.jetbrains.projector.server.util.unprotect
import org.jetbrains.projector.util.logging.Logger
import java.awt.RenderingHints
import javax.swing.UIManager

object SettingsInitializer {

  private fun getIdeaComponentAntiAliasing(ideaClassLoader: ClassLoader): Any? {
    // this can't be run before IDEA's settings initialization
    val aaTextInfo = Class.forName("com.intellij.ide.ui.AntialiasingType", false, ideaClassLoader)
      .getDeclaredMethod("getAAHintForSwingComponent")
      .invoke(null)

    return aaTextInfo::class.java.getDeclaredField("aaHint").get(aaTextInfo)
  }

  private fun setDefaultComponentTextAa(aaHint: Any?) {
    // this can't be run before IDEA initialization too:
    // JComponents crash with `no ComponentUI class for: javax.swing.J***` (for example, JLabel)

    UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, aaHint)
  }

  private fun fixComponentAntiAliasing(ideaClassLoader: ClassLoader) {
    val aaHint = try {
      // todo: we do this only once so this won't work if a user changes AA settings in IDEA
      val ideaComponentAntiAliasing = getIdeaComponentAntiAliasing(ideaClassLoader)

      logger.debug { "Found IDEA aa: $ideaComponentAntiAliasing" }

      ideaComponentAntiAliasing
    }
    catch (t: Throwable) {
      logger.debug(t) { "Can't find IDEA AntiAliasing settings. It's OK if you don't run an IntelliJ platform based app." }

      PGraphics2D.defaultAa
    }

    setDefaultComponentTextAa(aaHint)
  }

  private fun disableSmoothScrolling(ideaClassLoader: ClassLoader) {
    val uiSettingsClass = Class.forName("com.intellij.ide.ui.UISettings", false, ideaClassLoader)

    val uiSettings = uiSettingsClass
      .getDeclaredMethod("getInstanceOrNull")
      .invoke(null)

    val uiSettingsState = uiSettingsClass
      .getDeclaredField("state").let {
        it.unprotect()

        it.get(uiSettings)
      }

    Class.forName("com.intellij.ide.ui.UISettingsState", false, ideaClassLoader)
      .getDeclaredMethod("setSmoothScrolling", Boolean::class.java)
      .invoke(uiSettingsState, false)
  }

  private fun onIdeaInitialization(ideaClassLoader: ClassLoader) {
    fixComponentAntiAliasing(ideaClassLoader)
    disableSmoothScrolling(ideaClassLoader)
  }

  fun addTaskToInitializeIdea() {
    invokeWhenIdeaIsInitialized("initialize IDEA: fix AA and disable smooth scrolling (at start)", onInitialized = ::onIdeaInitialization)
  }

  private val logger = Logger<SettingsInitializer>()
}
