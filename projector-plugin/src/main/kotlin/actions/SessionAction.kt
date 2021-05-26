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
package actions
import ProjectorService
import ui.SessionDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import isProjectorRunning

class SessionAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    check(ProjectorService.isSessionRunning) {
      "Projector session is not started"
    }

    val project = PlatformDataKeys.PROJECT.getData(e.dataContext)
    val sessionDialog = SessionDialog(project)
    sessionDialog.pack()
    sessionDialog.show()

    if (sessionDialog.exitCode == DialogWrapper.OK_EXIT_CODE) {
      ProjectorService.currentSession.apply {
        rwToken = sessionDialog.rwToken
        roToken = sessionDialog.roToken
        confirmConnection = sessionDialog.confirmConnection
        autostart = sessionDialog.autostart
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isProjectorRunning()
  }

  companion object {
    const val ID = "projector.session"
  }
}
