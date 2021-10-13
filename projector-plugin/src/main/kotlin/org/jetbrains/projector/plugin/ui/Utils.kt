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
import org.jetbrains.projector.plugin.actions.ProjectorActionGroup
import org.jetbrains.projector.plugin.getIdeStatusBar

fun installUI(project: Project) {
  if (!installProjectorWidget(project)) {
    installMenu()
  }
}

fun installMenu() {
  ProjectorActionGroup.show()
  displayNotification("Warning", "Can't display status bar widget",
                      "Use Projector menu to manage plugin")
}

fun installProjectorWidget(project: Project): Boolean {
  val statusBar = getIdeStatusBar(project) ?: return false

  if (statusBar.getWidget(ProjectorStatusWidget.ID) != null) return true // already installed

  val method = try {
    StatusBar::class.java.getMethod("addWidget", StatusBarWidget::class.java, String::class.java)
  }
  catch (e: NoSuchMethodException) {
    val logger = Logger.getInstance("ProjectorWidget UI")
    logger.error("StatusBar widget is unsupported in this IDEA version: StatusBar has no addWidget method")
    null
  }

  val ret = method != null

  method?.let {
    val widget = ProjectorStatusWidget(statusBar)
    it.invoke(statusBar, widget, StatusBar.Anchors.DEFAULT_ANCHOR)
    widget.update()
  }

  return ret
}

fun removeUI(project: Project) {
  ProjectorActionGroup.hide()
  removeProjectorWidget(project)
}

private fun removeProjectorWidget(project: Project) {
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

