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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt

import org.jetbrains.projector.awt.peer.PComponentJdk11Peer
import org.jetbrains.projector.awt.peer.PMenuJdk11Peer
import org.jetbrains.projector.awt.peer.base.*
import sun.awt.LightweightFrame
import java.awt.*
import java.awt.List

object PPeerFactoryJdk11 : PPeerFactory {

  override fun createButton(target: Button): PButtonPeerBase = object : PButtonPeerBase(target), PComponentJdk11Peer {}

  override fun createTextField(target: TextField): PTextFieldPeerBase = object : PTextFieldPeerBase(target), PComponentJdk11Peer {}

  override fun createLabel(target: Label): PLabelPeerBase = object : PLabelPeerBase(target), PComponentJdk11Peer {}

  override fun createList(target: List): PListPeerBase = object : PListPeerBase(target), PComponentJdk11Peer {}

  override fun createCheckbox(target: Checkbox): PCheckboxPeerBase = object : PCheckboxPeerBase(target), PComponentJdk11Peer {}

  override fun createScrollbar(target: Scrollbar): PScrollbarPeerBase = object : PScrollbarPeerBase(target), PComponentJdk11Peer {}

  override fun createScrollPane(target: ScrollPane): PScrollPanePeerBase = object : PScrollPanePeerBase(target), PComponentJdk11Peer {}

  override fun createTextArea(target: TextArea): PTextAreaPeerBase = object : PTextAreaPeerBase(target), PComponentJdk11Peer {}

  override fun createChoice(target: Choice): PChoicePeerBase = object : PChoicePeerBase(target), PComponentJdk11Peer {}

  override fun createFrame(target: Frame): PFramePeerBase = object : PFramePeerBase(target), PComponentJdk11Peer {}

  override fun createCanvas(target: Canvas): PCanvasPeerBase = object : PCanvasPeerBase(target), PComponentJdk11Peer {}

  override fun createPanel(target: Panel): PPanelPeerBase = object : PPanelPeerBase(target), PComponentJdk11Peer {}

  override fun createWindow(target: Window): PWindowPeerBase = object : PWindowPeerBase(target), PComponentJdk11Peer {}

  override fun createDialog(target: Dialog): PDialogPeerBase = object : PDialogPeerBase(target), PComponentJdk11Peer {}

  override fun createMenu(target: Menu): PMenuPeerBase = object : PMenuPeerBase(), PMenuJdk11Peer {}

  override fun createPopupMenu(target: PopupMenu): PPopupMenuPeerBase = object : PPopupMenuPeerBase(), PMenuJdk11Peer {}

  override fun createFileDialog(target: FileDialog): PFileDialogPeerBase = object : PFileDialogPeerBase(target), PComponentJdk11Peer {}

  override fun createLightweightFrame(target: LightweightFrame): PFramePeerBase = object : PFramePeerBase(target), PComponentJdk11Peer {}

}
