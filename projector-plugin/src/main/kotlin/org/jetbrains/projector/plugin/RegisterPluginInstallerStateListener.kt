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

package org.jetbrains.projector.plugin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import org.jetbrains.projector.plugin.actions.ProjectorActionGroup
import org.jetbrains.projector.plugin.ui.ProjectorStatusWidget
import org.jetbrains.projector.plugin.ui.displayNotification


class RegisterPluginInstallerStateListener : StartupActivity, DumbAware {
  private val logger = Logger.getInstance(RegisterPluginInstallerStateListener::class.java)

  override fun runActivity(project: Project) {
    PluginInstaller.addStateListener(object : PluginStateListener {
      override fun install(descriptor: IdeaPluginDescriptor) {}

      override fun uninstall(descriptor: IdeaPluginDescriptor) {
        removeUI(project)
        ProjectorService.autostart = false

        if (isProjectorRunning()) {
          ProjectorService.disable()
        }
      }
    })

    installMenu(project)
    installUI(project)
    ProjectorService.autostartIfRequired()
  }

  private fun installUI(project: Project) {
    if (!installProjectorWidget(project)) {
      installMenu(project)
    }
  }

  private fun installMenu(project: Project) {
    ProjectorActionGroup.show()
    displayNotification(project, "Warning", "Can't display status bar widget",
                        "Use Projector menu to manage plugin")
  }

  private fun installProjectorWidget(project: Project): Boolean {
    val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return false

    if (statusBar.getWidget(ProjectorStatusWidget.ID) != null) return true // already installed

    val method = try {
      StatusBar::class.java.getMethod("addWidget", StatusBarWidget::class.java, String::class.java)
    }
    catch (e: NoSuchMethodException) {
      logger.error("StatusBar widget is unsupported in this IDEA version: StatusBar has no addWidget method")
      null
    }

    val ret = method != null

    method?.let {
      val widget = ProjectorStatusWidget(project)
      it.invoke(statusBar, widget, StatusBar.Anchors.DEFAULT_ANCHOR)
      widget.update()
    }

    return ret
  }

  private fun removeUI(project: Project) {
    ProjectorActionGroup.hide()
    removeProjectorWidget(project)
  }

  private fun removeProjectorWidget(project: Project) {
    val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return

    val method = try {
      StatusBar::class.java.getMethod("removeWidget", String::class.java)
    }
    catch (e: NoSuchMethodException) {
      logger.error("StatusBar has no removeWidget method")
      null
    }

    method?.invoke(statusBar, ProjectorStatusWidget.ID)
  }
}
