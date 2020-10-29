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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.projector.server.ProjectorServer
import javax.swing.JOptionPane

class CopyUrlAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    require(ProjectorService.instance.currentSession != null) {
      "Projector session is not started"
    }

    val rwOption = "Full Access"
    val roOption = "View Only"
    val cancelOption = "Cancel"
    val invitationDialogOptions = arrayOf(rwOption, roOption, cancelOption)

    val selectedInvitationOption = JOptionPane.showOptionDialog(
      null,
      "Copy the link depending on the type of access you want to grant.",
      "Copy Invitation Link",
      JOptionPane.DEFAULT_OPTION,
      JOptionPane.PLAIN_MESSAGE,
      null,
      invitationDialogOptions,
      null,
    )

    val tokenPropertyName = when (invitationDialogOptions[selectedInvitationOption]) {
      roOption -> ProjectorServer.RO_TOKEN_ENV_NAME
      rwOption -> ProjectorServer.TOKEN_ENV_NAME
      else -> return
    }

    ProjectorService.instance.currentSession!!.copyInvitationLink(tokenPropertyName)
  }

  override fun update(e: AnActionEvent) {
    val state = ProjectorService.instance.enabled == EnabledState.HAS_VM_OPTIONS_AND_ENABLED
    e.presentation.isEnabled = state
    e.presentation.isVisible = state
  }
}