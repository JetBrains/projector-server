/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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
package org.jetbrains.projector.plugin.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import org.jetbrains.projector.plugin.getIdeStatusBar

private fun isWidgetFactoryAvailable(): Boolean {
  val cls = try {
    Class.forName("com.intellij.openapi.wm.StatusBarWidgetFactory",
                  false,
                  ProjectorStatusWidget::class.java.classLoader)
  }
  catch (e: ClassNotFoundException) {
    null
  }

  return cls != null
}

// Platform 193 compatibility functions
fun installProjectorWidgetIfRequired(project: Project) {
  if (isExistWidgetFactory()) return

  val statusBar = getIdeStatusBar(project) ?: return

  if (statusBar.getWidget(ProjectorStatusWidget.ID) != null) return

  val method = try {
    StatusBar::class.java.getMethod("addWidget", StatusBarWidget::class.java, String::class.java)
  }
  catch (e: NoSuchMethodException) {
    val logger = Logger.getInstance("ProjectorWidget UI")
    logger.error("StatusBar widget is unsupported in this IDEA version: StatusBar has no addWidget method")
    null
  }

  method?.let {
    val widget = ProjectorStatusWidget(statusBar)
    it.invoke(statusBar, widget, StatusBar.Anchors.DEFAULT_ANCHOR)
    widget.update()
  }
}

fun removeProjectorWidgetIfRequired(project: Project) {
  if (isExistWidgetFactory()) return

  val statusBar = getIdeStatusBar(project) ?: return

  val method = try {
    StatusBar::class.java.getMethod("removeWidget", String::class.java)
  }
  catch (e: NoSuchMethodException) {
    val logger = Logger.getInstance("ProjectorWidget UI")
    logger.error("StatusBar has no removeWidget method")
    null
  }

  method?.invoke(statusBar, ProjectorStatusWidget.ID)
}

