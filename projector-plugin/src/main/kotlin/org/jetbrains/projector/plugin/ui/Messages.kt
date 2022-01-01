/*
 * Copyright (c) 2019-2022, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItMessage
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.PositionTracker
import org.jetbrains.projector.plugin.ProjectorInstallStateKeeper
import org.jetbrains.projector.plugin.getIdeStatusBar


// This function shows tooltip message above Projector status bar widget.
// It can fail if widget was not added to status bar yet.
// Caller can check it, by analyzing return value
internal fun showMessage(project: Project, header: String, message: String): Boolean {
  val sb = getIdeStatusBar(project) ?: return false
  val widget = sb.getWidget(ProjectorStatusWidget.ID) ?: return false

  if (widget is ProjectorStatusWidget) {
    val gotItMessage = GotItMessage.createMessage(header, message).setDisposable(widget)

    gotItMessage.show(
      object : PositionTracker<Balloon>(widget.component) {
        override fun recalculateLocation(baloon: Balloon) = RelativePoint.getCenterOf(widget.component)
      },
      Balloon.Position.above,
    )

  }

  return true
}

class HelloMessage(private val project: Project) {
  private val alarm = Alarm()
  private val installStateKeeper = ProjectorInstallStateKeeper.getInstance()

  private val isRequired: Boolean
    get() = isFirstRun() && longTimeSinceLastHello()

  private fun isFirstRun() = installStateKeeper.isFirstRun

  private fun longTimeSinceLastHello() = installStateKeeper.sinceLastHello() >
                                         ProjectorInstallStateKeeper.FULL_DAY

  fun sayHelloIfRequired() {
    if (isRequired) {
      alarm.addRequest({ show() }, 1000)
      installStateKeeper.setLastHelloTime()
    }
  }

  private fun show() {
    val shown = showMessage(project, "Hello!",
                            "To start using the Projector plugin,\nclick the widget below")

    if (!shown) {
      alarm.addRequest({ show() }, 1000)
    }
  }
}
