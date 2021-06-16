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

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import org.jetbrains.projector.plugin.*
import org.jetbrains.projector.plugin.actions.*
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.Icon
import javax.swing.SwingUtilities

class ProjectorStatusWidget(project: Project)
  : EditorBasedWidget(project),
    StatusBarWidget.MultipleTextValuesPresentation,
    StatusBarWidget.Multiframe,
    ProjectorStateListener,
    PropertyChangeListener {

  private var clients = 0

  override fun ID(): String = ID

  override fun copy(): StatusBarWidget = ProjectorStatusWidget(project)

  override fun getPopupStep(): ListPopup? {
    onClick()
    return null
  }

  override fun getTooltipText(): String = updateTooltip()

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun getSelectedValue(): String = updateText()

  override fun getPresentation(): WidgetPresentation = this

  override fun getIcon() = updateIcon()

  override fun install(statusBar: StatusBar) {
    super.install(statusBar)
    ProjectorService.subscribe(this)
    update()
  }

  override fun dispose() {
    ProjectorService.unsubscribe(this)
    ProjectorService.removeClientsObserver(this)
    super.dispose()
  }

  fun update() {
    myStatusBar?.updateWidget(ID())
  }

  override fun stateChanged() {
    if (isProjectorRunning()) {
      ProjectorService.addClientsObserver(this)
    }

    update()
  }

  private fun updateIcon(): Icon {
    return when {
      isActivationNeeded() -> ACTIVATION_NEEDED_SIGN
      isProjectorRunning() -> RUNNING_SIGN
      isProjectorAutoStarting() -> STARTING_SIGN
      isProjectorDisabled() -> DISABLED_SIGN
      isProjectorStopped() -> STOPPED_SIGN
      else -> DISABLED_SIGN
    }
  }

  private fun updateText(): String {
    if (isProjectorRunning()) {
      return "Projector ($clients)"
    }

    return "Projector"
  }

  private fun updateTooltip(): String {
    return when {
      isActivationNeeded() -> "Activation is needed"
      isProjectorRunning() -> "Projector is running. Connected clients: $clients"
      isProjectorAutoStarting() -> "Projector is starting"
      isProjectorDisabled() -> "Projector is disabled"
      isHeadlessProjectorDetected() -> "Headless projector detected, plugin is disabled"
      isProjectorStopped() -> "Projector is stopped"
      else -> "Impossible state"
    }
  }

  private fun onClick() {
    when {
      isActivationNeeded() -> fireAction(ActivateAction.ID)
      isProjectorRunning() -> fireAction(SessionAction.ID)
      isProjectorAutoStarting() -> fireAction(WaitForStartAction.ID)
      isProjectorDisabled() -> fireAction(EnableAction.ID)
      isHeadlessProjectorDetected() -> fireAction(HeadlessProjectorAction.ID)
      isProjectorStopped() -> fireAction(EnableAction.ID)
    }
  }

  private fun fireAction(actionId: String) {
    val am = ActionManager.getInstance()
    val action = am.getAction(actionId)
    val ctx = DataManager.getInstance().getDataContext(myStatusBar as Component)
    val event = AnActionEvent.createFromAnAction(action, null, "", ctx)
    action.actionPerformed(event)
  }

  override fun propertyChange(event: PropertyChangeEvent?) {
    event?.let {
      clients = event.newValue as Int
      SwingUtilities.invokeLater { update() }
    }
  }

  companion object {
    val ID: String by lazy { ProjectorStatusWidget::class.java.name }
    private fun getIcon(path: String): Icon = IconLoader.getIcon(path, ProjectorStatusWidget::class.java)
    private val ACTIVATION_NEEDED_SIGN: Icon by lazy { getIcon("/META-INF/activationNeeded.svg") }
    private val RUNNING_SIGN: Icon by lazy { getIcon("/META-INF/runningSign.svg") }
    private val STARTING_SIGN: Icon by lazy { getIcon("/META-INF/startingSign.svg") }
    private val DISABLED_SIGN: Icon by lazy { getIcon("/META-INF/disabledSign.svg") }
    private val STOPPED_SIGN: Icon by lazy { getIcon("/META-INF/stoppedSign.svg") }
  }
}
