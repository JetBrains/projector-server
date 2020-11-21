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

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*

class LinearPanelBuilder(private var panelWrapper: JPanel) {
  private val constraints = GridBagConstraints()

  init {
    panelWrapper.componentOrientation = ComponentOrientation.LEFT_TO_RIGHT
    panelWrapper.layout = GridBagLayout()
    constraints.fill = GridBagConstraints.HORIZONTAL
    constraints.gridx = 0
    constraints.gridy = 0
  }

  fun addNextComponent(
    c: Component, gridCount: Int = 1, width: Double = 1.0,
    leftGap: Int = 0, rightGap: Int = 0, topGap: Int = 0, bottomGap: Int = 0,
  ): LinearPanelBuilder {
    constraints.gridwidth = gridCount
    constraints.weightx = width
    constraints.insets = Insets(topGap, leftGap, bottomGap, rightGap)
    panelWrapper.add(c, constraints)
    constraints.gridx += gridCount
    return this
  }

  fun startNextLine(): LinearPanelBuilder {
    constraints.gridx = 0
    constraints.gridy += 1
    return this
  }
}
