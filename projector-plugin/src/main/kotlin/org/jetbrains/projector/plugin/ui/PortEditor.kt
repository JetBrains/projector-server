/*
 * Copyright (c) 2019-2024, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

import org.jetbrains.projector.server.ProjectorServer
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

internal class PortEditor(portValue: String) : JPanel() {
  private val title = JLabel("Port:")
  private val port: JTextField = JTextField(4).apply {
    text = portValue.takeIf(String::isNotEmpty) ?: ProjectorServer.getEnvPort().toString()

    addKeyListener(object : KeyAdapter() {
      override fun keyReleased(e: KeyEvent) {
        onChange?.invoke()
      }
    })
  }

  init {
    LinearPanelBuilder(this)
      .addNextComponent(title, gridWidth = 2)
      .addNextComponent(port)
  }

  var onChange: (() -> Unit)? = null
  var value
    get() = port.text ?: ""
    set(value) {
      port.text = value
    }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    port.isEnabled = enabled
  }
}
