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

import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.function.BiConsumer
import java.util.function.Consumer

class MarkdownPanelUpdater(
  private val showCallback: BiConsumer<Int, Boolean>,
  private val resizeCallback: BiConsumer<Int, Dimension>,
  private val moveCallback: BiConsumer<Int, Point>,
  private val disposeCallback: Consumer<Int>,
  private val placeToWindowCallback: BiConsumer<Int, Component?>,
  private val setHtmlCallback: BiConsumer<Int, String>,
  private val setCssCallback: BiConsumer<Int, String>,
  private val scrollCallback: BiConsumer<Int, Int>,
  private val browseUriCallback: Consumer<String>,
) {

  private lateinit var ideaClassLoader: ClassLoader

  private val extensionPointNameClass by lazy {
    Class.forName("com.intellij.openapi.extensions.ExtensionPointName", false, ideaClassLoader)
  }

  private val extensionPointNameCreateMethod by lazy {
    extensionPointNameClass.getDeclaredMethod("create", String::class.java)
  }

  private val extensionPointNameGetExtensionsMethod by lazy {
    extensionPointNameClass.getDeclaredMethod("getExtensions")
  }

  private lateinit var update: () -> Unit

  private lateinit var open: (String) -> Unit

  fun setUpCallbacks() {
    invokeWhenIdeaIsInitialized("set up markdown callbacks") { ideaClassLoader ->
      this.ideaClassLoader = ideaClassLoader

      val extensionPointName = extensionPointNameCreateMethod.invoke(null, OUR_EXTENSION_ID)

      val extensions = extensionPointNameGetExtensionsMethod.invoke(extensionPointName) as Array<*>

      val projectorExtension = extensions.filterNotNull().single { "Projector" in it::class.java.name }

      val projectorExtensionClass = projectorExtension::class.java

      val updateAllMethod = projectorExtensionClass
        .getDeclaredMethod("updateAll")

      update = { updateAllMethod.invoke(null) }

      val openInExternalBrowserMethod = projectorExtensionClass
        .getDeclaredMethod("openInExternalBrowser", String::class.java)

      open = { openInExternalBrowserMethod.invoke(null, it) }

      projectorExtensionClass
        .getDeclaredMethod("setShowCallback", BiConsumer::class.java)
        .invoke(null, showCallback)

      projectorExtensionClass
        .getDeclaredMethod("setResizeCallback", BiConsumer::class.java)
        .invoke(null, resizeCallback)

      projectorExtensionClass
        .getDeclaredMethod("setMoveCallback", BiConsumer::class.java)
        .invoke(null, moveCallback)

      projectorExtensionClass
        .getDeclaredMethod("setDisposeCallback", Consumer::class.java)
        .invoke(null, disposeCallback)

      projectorExtensionClass
        .getDeclaredMethod("setPlaceToWindowCallback", BiConsumer::class.java)
        .invoke(null, placeToWindowCallback)

      projectorExtensionClass
        .getDeclaredMethod("setSetHtmlCallback", BiConsumer::class.java)
        .invoke(null, setHtmlCallback)

      projectorExtensionClass
        .getDeclaredMethod("setSetCssCallback", BiConsumer::class.java)
        .invoke(null, setCssCallback)

      projectorExtensionClass
        .getDeclaredMethod("setScrollCallback", BiConsumer::class.java)
        .invoke(null, scrollCallback)

      projectorExtensionClass
        .getDeclaredMethod("setBrowseUriCallback", Consumer::class.java)
        .invoke(null, browseUriCallback)
    }
  }

  fun updateAll() {
    if (this::update.isInitialized) {
      update()
    }
  }

  fun openInExternalBrowser(link: String) {
    open(link)
  }

  companion object {

    private const val OUR_EXTENSION_ID = "org.jetbrains.projector.markdown.html.panel.provider"
  }
}
