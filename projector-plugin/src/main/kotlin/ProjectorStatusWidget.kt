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

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class ProjectorStatusWidget(project: Project)
  : EditorBasedWidget(project),
    StatusBarWidget.MultipleTextValuesPresentation,
    StatusBarWidget.Multiframe {
  private val logger = Logger.getInstance(ProjectorStatusWidget::class.java.name)

  override fun ID(): String = ProjectorStatusWidget::class.java.name

  override fun copy(): StatusBarWidget = ProjectorStatusWidget(project)

  override fun getPopupStep(): ListPopup {
    return JBPopupFactory.getInstance().createActionGroupPopup("TITLE Projector",
                                                               actions,
                                                               DataContext.EMPTY_CONTEXT,
                                                               false,
                                                               null,
                                                               5)
  }

  override fun getTooltipText(): String = "Projector tooltip"

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun getSelectedValue(): String = "Projector"

  override fun getPresentation(): WidgetPresentation = this

  override fun getIcon() = GREEN_DOT

  companion object {
    private val actions = object : ActionGroup() {
      override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(ActivateAction(),
                                                                             EnableAction(),
                                                                             DisableAction(),
                                                                             HeadlessProjectorAction(),
                                                                             SessionAction())
    }

    @JvmField
    val RED_DOT = IconLoader.getIcon("/META-INF/redSign.svg", ProjectorStatusWidget::class.java)

    @JvmField
    val GREEN_DOT = IconLoader.getIcon("/META-INF/greenSign.svg", ProjectorStatusWidget::class.java)

    @JvmField
    val YELLOW_DOT = IconLoader.getIcon("/META-INF/yellowSign.svg", ProjectorStatusWidget::class.java)

  }


}
