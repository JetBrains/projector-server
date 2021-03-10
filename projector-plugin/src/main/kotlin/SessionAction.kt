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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper

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
        host = sessionDialog.listenAddress
        rwToken = sessionDialog.rwToken
        roToken = sessionDialog.roToken
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val state = ProjectorService.enabled == EnabledState.HAS_VM_OPTIONS_AND_ENABLED
    e.presentation.isEnabled = state
    e.presentation.isVisible = state
  }
}
