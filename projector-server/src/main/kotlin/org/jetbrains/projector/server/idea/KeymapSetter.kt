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
package org.jetbrains.projector.server.idea

import org.jetbrains.projector.common.protocol.data.UserKeymap
import org.jetbrains.projector.server.core.ij.invokeWhenIdeaIsInitialized
import org.jetbrains.projector.util.logging.Logger
import javax.swing.SwingUtilities

object KeymapSetter {

  private val logger = Logger<KeymapSetter>()

  private fun UserKeymap.toKeyMapManagerFieldName() = when (this) {
    UserKeymap.WINDOWS -> "X_WINDOW_KEYMAP"
    UserKeymap.MAC -> "MAC_OS_X_10_5_PLUS_KEYMAP"
    UserKeymap.LINUX -> "GNOME_KEYMAP"
  }

  fun setKeymap(keymap: UserKeymap) {
    invokeWhenIdeaIsInitialized("set keymap to match user's OS ($keymap)") { ideaClassLoader ->
      SwingUtilities.invokeLater {
        // it should be done on EDT
        val keymapManagerClass = Class.forName("com.intellij.openapi.keymap.KeymapManager", false, ideaClassLoader)

        val userKeymapName = keymapManagerClass
          .getDeclaredField(keymap.toKeyMapManagerFieldName())
          .get(null) as String

        val keymapManagerExClass = Class.forName("com.intellij.openapi.keymap.ex.KeymapManagerEx", false, ideaClassLoader)

        val keymapManagerExInstance = keymapManagerExClass
          .getDeclaredMethod("getInstanceEx")
          .invoke(null)

        if (keymapManagerExInstance == null) {
          logger.error { "getInstanceEx() == null - skipping setting keymap" }
          return@invokeLater
        }

        val keymapInstance = keymapManagerClass
          .getDeclaredMethod("getKeymap", String::class.java)
          .invoke(keymapManagerExInstance, userKeymapName)

        if (keymapInstance == null) {
          logger.error { "getKeymap($userKeymapName) == null - skipping setting keymap" }
          return@invokeLater
        }

        val keymapClass = Class.forName("com.intellij.openapi.keymap.Keymap", false, ideaClassLoader)

        keymapManagerExClass
          .getDeclaredMethod("setActiveKeymap", keymapClass)
          .invoke(keymapManagerExInstance, keymapInstance)
      }
    }
  }
}
