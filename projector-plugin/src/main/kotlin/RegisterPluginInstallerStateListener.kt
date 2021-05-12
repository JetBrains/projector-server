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
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import ui.ProjectorStatusWidget


class RegisterPluginInstallerStateListener : StartupActivity {
  private val logger = Logger.getInstance(RegisterPluginInstallerStateListener::class.java)

  override fun runActivity(project: Project) {
    PluginInstaller.addStateListener(object : PluginStateListener {
      override fun install(descriptor: IdeaPluginDescriptor) {}

      override fun uninstall(descriptor: IdeaPluginDescriptor) {
        ProjectorService.autostart = false

        if (isProjectorRunning()) {
          ProjectorService.disable()
        }
      }
    })

    //val n = Notification("Projector warning", "")
    //Notification.fire(n)

    installMenu()
    installUI(project)
    ProjectorService.autostartIfRequired()
  }

  private fun installUI(project: Project) {
    if (!installProjectorWidget(project)) {
      installMenu()
    }
  }

  private fun installMenu() {
    val actionGroup = getProjectorActionGroup()
    ActionManager.getInstance().createActionPopupMenu(ActionPlaces.MAIN_MENU, actionGroup)
  }

  private fun installProjectorWidget(project: Project): Boolean {
    val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return false

    val method = try {
      StatusBar::class.java.getMethod("addWidget", StatusBarWidget::class.java, String::class.java)
    }
    catch (e: NoSuchMethodException) {
      logger.error("Toolbar widget is unsupported in this IDEA version: StatusBar has no addWidget method")
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
}
