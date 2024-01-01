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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt

import org.jetbrains.projector.awt.peer.base.*
import sun.awt.LightweightFrame
import java.awt.*
import java.awt.List

object PPeerFactoryJdk17 : PPeerFactory {

  override fun createButton(target: Button): PButtonPeerBase = object : PButtonPeerBase(target) {}

  override fun createTextField(target: TextField): PTextFieldPeerBase = object : PTextFieldPeerBase(target) {}

  override fun createLabel(target: Label): PLabelPeerBase = object : PLabelPeerBase(target) {}

  override fun createList(target: List): PListPeerBase = object : PListPeerBase(target) {}

  override fun createCheckbox(target: Checkbox): PCheckboxPeerBase = object : PCheckboxPeerBase(target) {}

  override fun createScrollbar(target: Scrollbar): PScrollbarPeerBase = object : PScrollbarPeerBase(target) {}

  override fun createScrollPane(target: ScrollPane): PScrollPanePeerBase = object : PScrollPanePeerBase(target) {}

  override fun createTextArea(target: TextArea): PTextAreaPeerBase = object : PTextAreaPeerBase(target) {}

  override fun createChoice(target: Choice): PChoicePeerBase = object : PChoicePeerBase(target) {}

  override fun createFrame(target: Frame): PFramePeerBase = object : PFramePeerBase(target) {}

  override fun createCanvas(target: Canvas): PCanvasPeerBase = object : PCanvasPeerBase(target) {}

  override fun createPanel(target: Panel): PPanelPeerBase = object : PPanelPeerBase(target) {}

  override fun createWindow(target: Window): PWindowPeerBase = object : PWindowPeerBase(target) {}

  override fun createDialog(target: Dialog): PDialogPeerBase = object : PDialogPeerBase(target) {}

  override fun createMenu(target: Menu): PMenuPeerBase = object : PMenuPeerBase() {}

  override fun createPopupMenu(target: PopupMenu): PPopupMenuPeerBase = object : PPopupMenuPeerBase() {}

  override fun createFileDialog(target: FileDialog): PFileDialogPeerBase = object : PFileDialogPeerBase(target) {}

  override fun createLightweightFrame(target: LightweightFrame): PFramePeerBase = object : PFramePeerBase(target) {}

}
