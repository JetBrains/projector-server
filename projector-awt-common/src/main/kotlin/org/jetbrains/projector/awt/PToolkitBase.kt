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

import org.jetbrains.projector.awt.peer.*
import org.jetbrains.projector.awt.peer.base.PComponentPeerBase
import sun.awt.*
import sun.awt.image.ByteArrayImageSource
import sun.awt.image.FileImageSource
import sun.awt.image.ToolkitImage
import sun.awt.image.URLImageSource
import sun.font.FontDesignMetrics
import java.awt.*
import java.awt.Dialog.ModalExclusionType
import java.awt.Dialog.ModalityType
import java.awt.List
import java.awt.datatransfer.Clipboard
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.InvalidDnDOperationException
import java.awt.dnd.peer.DragSourceContextPeer
import java.awt.event.InputEvent
import java.awt.font.TextAttribute
import java.awt.im.InputMethodHighlight
import java.awt.im.spi.InputMethodDescriptor
import java.awt.image.ColorModel
import java.awt.image.ImageObserver
import java.awt.image.ImageProducer
import java.awt.peer.*
import java.net.URL
import java.util.*

abstract class PToolkitBase(private val peerFactory: PPeerFactory) : SunToolkit(), KeyboardFocusManagerPeerProvider, ComponentFactory {

  override fun createDesktopPeer(target: Desktop): DesktopPeer {
    return PDesktopPeer()
  }

  override fun createButton(target: Button): ButtonPeer {
    return peerFactory.createButton(target).registered()
  }

  override fun createTextField(target: TextField): TextFieldPeer {
    return peerFactory.createTextField(target).registered()
  }

  override fun createLabel(target: Label): LabelPeer {
    return peerFactory.createLabel(target).registered()
  }

  override fun createList(target: List): ListPeer {
    return peerFactory.createList(target).registered()
  }

  override fun createCheckbox(target: Checkbox): CheckboxPeer {
    return peerFactory.createCheckbox(target).registered()
  }

  override fun createScrollbar(target: Scrollbar): ScrollbarPeer {
    return peerFactory.createScrollbar(target).registered()
  }

  override fun createScrollPane(target: ScrollPane): ScrollPanePeer {
    return peerFactory.createScrollPane(target).registered()
  }

  override fun createTextArea(target: TextArea): TextAreaPeer {
    return peerFactory.createTextArea(target).registered()
  }

  override fun createChoice(target: Choice): ChoicePeer {
    return peerFactory.createChoice(target).registered()
  }

  override fun createFrame(target: Frame): FramePeer {
    return peerFactory.createFrame(target).registered()
  }

  override fun createCanvas(target: Canvas): CanvasPeer {
    return peerFactory.createCanvas(target).registered()
  }

  override fun createPanel(target: Panel): PanelPeer {
    return peerFactory.createPanel(target).registered()
  }

  override fun createWindow(target: Window): WindowPeer {
    return peerFactory.createWindow(target).registered()
  }

  override fun createDialog(target: Dialog): DialogPeer {
    return peerFactory.createDialog(target).registered()
  }

  override fun createMenuBar(target: MenuBar): MenuBarPeer {
    return PMenuBarPeer()  // todo: call targetCreatedPeer(target, it)
  }

  override fun createMenu(target: Menu): MenuPeer {
    return peerFactory.createMenu(target)  // todo: call targetCreatedPeer(target, it)
  }

  override fun createPopupMenu(target: PopupMenu): PopupMenuPeer {
    return peerFactory.createPopupMenu(target)  // todo: call targetCreatedPeer(target, it)
  }

  override fun createMenuItem(target: MenuItem): MenuItemPeer {
    return PMenuItemPeer()  // todo: call targetCreatedPeer(target, it)
  }

  override fun createFileDialog(target: FileDialog): FileDialogPeer {
    return peerFactory.createFileDialog(target).registered()
  }

  override fun createCheckboxMenuItem(target: CheckboxMenuItem): CheckboxMenuItemPeer {
    return PCheckboxMenuItemPeer()  // todo: call targetCreatedPeer(target, it)
  }

  override fun getFontPeer(name: String, style: Int): FontPeer? {
    return null
  }

  override fun getScreenSize(): Dimension {
    return Dimension(1024, 768)  // todo
  }

  override fun getScreenResolution(): Int {
    return 96  // todo
  }

  override fun getColorModel(): ColorModel {
    return ColorModel.getRGBdefault()
  }

  override fun getFontList(): Array<String> {
    return arrayOf(Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED, Font.DIALOG_INPUT)  // todo
  }

  override fun getFontMetrics(font: Font): FontMetrics {
    return FontDesignMetrics.getMetrics(font)
  }

  override fun sync() {
    // todo
  }

  override fun getImage(filename: String): Image? {
    return getImageFromHash(filename)
  }

  override fun getImage(url: URL): Image? {
    return getImageFromHash(url)
  }

  override fun createImage(filename: String): Image {
    return createImage(FileImageSource(filename))
  }

  override fun createImage(url: URL): Image {
    return createImage(URLImageSource(url))
  }

  override fun prepareImage(img: Image, w: Int, h: Int, o: ImageObserver?): Boolean {
    if (w == 0 || h == 0) {
      return true
    }

    if (img !is ToolkitImage) {
      return true
    }

    if (img.hasError()) {
      o?.imageUpdate(img, ImageObserver.ERROR or ImageObserver.ABORT, -1, -1, -1, -1)
      return false
    }

    return img.imageRep.prepare(o)
  }

  override fun checkImage(img: Image, w: Int, h: Int, o: ImageObserver?): Int {
    if (img !is ToolkitImage) {
      return ImageObserver.ALLBITS
    }

    val repBits: Int = if (w == 0 || h == 0) {
      ImageObserver.ALLBITS
    }
    else {
      img.imageRep.check(o)
    }

    return img.check(o) or repBits
  }

  override fun createImage(producer: ImageProducer): Image {
    return ToolkitImage(producer)
  }

  override fun createImage(data: ByteArray, offset: Int, length: Int): Image {
    return createImage(ByteArrayImageSource(data, offset, length))
  }

  override fun getPrintJob(frame: Frame, jobtitle: String, props: Properties): PrintJob? {
    return null
  }

  override fun beep() {
    System.out.write(0x07)
  }

  override fun getSystemClipboard(): Clipboard {
    return PClipboard
  }

  override fun getSystemEventQueueImpl(): EventQueue {
    return systemEventQueueImplPP
  }

  override fun createDragSourceContextPeer(dge: DragGestureEvent): DragSourceContextPeer {
    // throwing this exception is ok, it just indicates that
    // drag and drop is not supported
    throw InvalidDnDOperationException("Headless environment")
  }

  override fun isModalityTypeSupported(modalityType: ModalityType): Boolean {
    return true
  }

  override fun isModalExclusionTypeSupported(modalExclusionType: ModalExclusionType): Boolean {
    return false
  }

  override fun mapInputMethodHighlight(highlight: InputMethodHighlight): Map<TextAttribute, *> {
    return PInputMethod.mapInputMethodHighlight()
  }

  override fun getKeyboardFocusManagerPeer(): KeyboardFocusManagerPeer {
    return PKeyboardFocusManagerPeer
  }

  override fun createLightweightFrame(target: LightweightFrame): FramePeer {
    return peerFactory.createLightweightFrame(target)
  }

  override fun createTrayIcon(target: TrayIcon?): TrayIconPeer? = null

  override fun createSystemTray(target: SystemTray?): SystemTrayPeer? = null

  override fun isTraySupported(): Boolean = false

  override fun syncNativeQueue(timeout: Long): Boolean = true

  override fun grab(w: Window?) {
    // todo
  }

  override fun ungrab(w: Window?) {
    // todo
  }

  override fun isDesktopSupported(): Boolean = true

  override fun isTaskbarSupported(): Boolean = false

  override fun getMouseInfoPeer(): MouseInfoPeer = PMouseInfoPeer

  override fun getInputMethodAdapterDescriptor(): InputMethodDescriptor? = null

  override fun areExtraMouseButtonsEnabled(): Boolean {
    return true
  }

  override fun getNumberOfButtons(): Int {
    return 5 // this is cached in MouseEvent, so support at least mice with two side buttons
  }

  private fun getImageFromHash(filename: String): Image? {
    synchronized(imgCache) {
      var img: Image? = imgCache[filename] as Image?

      if (img == null) {
        try {
          img = createImage(FileImageSource(filename))
          imgCache[filename] = img
        }
        catch (_: Exception) {
        }
      }

      return img
    }
  }

  private fun getImageFromHash(url: URL): Image? {
    synchronized(imgCache) {
      var img: Image? = imgCache[url] as Image?

      if (img == null) {
        try {
          img = createImage(URLImageSource(url))
          imgCache[url] = img
        }
        catch (_: Exception) {
        }
      }

      return img
    }
  }

  @Suppress("DEPRECATION")  // as in superclass
  override fun isPrintableCharacterModifiersMask(mods: Int): Boolean {
    return when (macKeyboardModifiersMode) {
      true -> (mods and (InputEvent.META_MASK or InputEvent.CTRL_MASK)) == 0  // Mac
      false -> (mods and InputEvent.ALT_MASK) == (mods and InputEvent.CTRL_MASK)  // Linux and Windows
    }
  }

  companion object {

    var macKeyboardModifiersMode = false

    @Suppress("DEPRECATION")  // todo
    private val imgCache = SoftCache()

    val systemEventQueueImplPP: EventQueue
      get() = AppContext.getAppContext().get(AppContext.EVENT_QUEUE_KEY) as EventQueue

    private fun <Peer : PComponentPeerBase> Peer.registered() = apply {
      targetCreatedPeer(pWindow.target, this)
    }

    internal fun disposePeer(peer: PComponentPeerBase) {
      targetDisposedPeer(peer.pWindow.target, peer)
    }
  }
}
