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
package ui

import ProjectorService
import ProjectorStateListener
import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup

import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import getProjectorActionGroup
import isActivationNeeded
import isProjectorAutoStarting
import isProjectorDisabled
import isProjectorRunning
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon
import java.awt.Graphics
import java.awt.Color
import java.awt.Image


class ProjectorStatusWidget(project: Project)
  : EditorBasedWidget(project),
    StatusBarWidget.MultipleTextValuesPresentation,
    StatusBarWidget.Multiframe,
    ProjectorStateListener {

  override fun ID(): String = ProjectorStatusWidget::class.java.name

  override fun copy(): StatusBarWidget = ProjectorStatusWidget(project)

  override fun getPopupStep(): ListPopup {
    val context = DataManager.getInstance().getDataContext(myStatusBar as Component)
    val actionGroup = getProjectorActionGroup(PROJECTOR_TOOLBAR_ACTION_GROUP)

    return JBPopupFactory.getInstance().createActionGroupPopup("Projector",
                                                               actionGroup,
                                                               context,
                                                               false,
                                                               null,
                                                               5)
  }

  override fun getTooltipText(): String = updateTooltip()

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun getSelectedValue(): String = updateText()

  override fun getPresentation(): WidgetPresentation = this

  override fun getIcon() = updateIcon()

  override fun install(statusBar: StatusBar) {
    super.install(statusBar)
    ProjectorService.subscribe(this)
  }

  override fun dispose() {
    ProjectorService.unsubscribe(this)
    super.dispose()
  }

  fun update() = myStatusBar.updateWidget(ID())

  override fun stateChanged() = update()

  private fun updateIcon(): Icon {
    return when {
      isActivationNeeded() -> RED_DOT
      isProjectorRunning() -> GREEN_DOT
      isProjectorAutoStarting() -> YELLOW_DOT
      isProjectorDisabled() -> BLACK_DOT
      else -> BLACK_DOT
    }
  }

  private fun updateText() = "Projector"

  private fun updateTooltip(): String {
    return when {
      isActivationNeeded() -> "Activation is needed"
      isProjectorRunning() -> "Projector is running"
      isProjectorAutoStarting() -> "Projector is starting"
      isProjectorDisabled() -> "Projector is disabled"
      else -> "Impossible state"
    }
  }

  private companion object {
    private const val PROJECTOR_TOOLBAR_ACTION_GROUP = "projector.toolbar"
    private val RED_DOT: Icon by lazy { createIcon(Color.RED) }
    private val GREEN_DOT: Icon by lazy { createIcon(Color.GREEN) }
    private val YELLOW_DOT: Icon by lazy { createIcon(Color.YELLOW) }
    private val BLACK_DOT: Icon by lazy { createIcon(Color.BLACK) }

    private const val ICON_SIZE = 16
    private const val DOT_RADIUS = 3
    private const val SCALE_FACTOR = 4

    private fun createScaledImage(color: Color): BufferedImage {
      val scaledImage = BufferedImage(SCALE_FACTOR * ICON_SIZE, SCALE_FACTOR * ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
      val g: Graphics = scaledImage.graphics
      g.color = color
      val shift = SCALE_FACTOR * (ICON_SIZE.div(2) - DOT_RADIUS)
      g.fillOval(shift, shift, SCALE_FACTOR * DOT_RADIUS * 2, SCALE_FACTOR * DOT_RADIUS * 2)
      g.dispose()

      return scaledImage
    }

    private fun createIcon(color: Color): Icon {
      val scaledImage = createScaledImage(color)
      val image = scaledImage.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH)
      return ImageIcon(image)
    }
  }
}
