/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2023 JetBrains s.r.o.
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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt

import org.jetbrains.projector.awt.peer.base.*
import sun.awt.LightweightFrame
import java.awt.*
import java.awt.List

/**
 * Factory for peers that are different in JDK11 and JDK17
 */
interface PPeerFactory {

  fun createButton(target: Button): PButtonPeerBase

  fun createTextField(target: TextField): PTextFieldPeerBase

  fun createLabel(target: Label): PLabelPeerBase

  fun createList(target: List): PListPeerBase

  fun createCheckbox(target: Checkbox): PCheckboxPeerBase

  fun createScrollbar(target: Scrollbar): PScrollbarPeerBase

  fun createScrollPane(target: ScrollPane): PScrollPanePeerBase

  fun createTextArea(target: TextArea): PTextAreaPeerBase

  fun createChoice(target: Choice): PChoicePeerBase

  fun createFrame(target: Frame): PFramePeerBase

  fun createCanvas(target: Canvas): PCanvasPeerBase

  fun createPanel(target: Panel): PPanelPeerBase

  fun createWindow(target: Window): PWindowPeerBase

  fun createDialog(target: Dialog): PDialogPeerBase

  fun createMenu(target: Menu): PMenuPeerBase

  fun createPopupMenu(target: PopupMenu): PPopupMenuPeerBase

  fun createFileDialog(target: FileDialog): PFileDialogPeerBase

  fun createLightweightFrame(target: LightweightFrame): PFramePeerBase
  
}
