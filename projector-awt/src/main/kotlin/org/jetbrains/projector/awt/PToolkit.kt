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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt

import org.jetbrains.projector.awt.peer.*
import sun.awt.AppContext
import sun.awt.ComponentFactory
import sun.awt.KeyboardFocusManagerPeerProvider
import sun.awt.SoftCache
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
import java.awt.font.TextAttribute
import java.awt.im.InputMethodHighlight
import java.awt.image.ColorModel
import java.awt.image.ImageObserver
import java.awt.image.ImageProducer
import java.awt.peer.*
import java.net.URL
import java.util.*

class PToolkit : Toolkit(), KeyboardFocusManagerPeerProvider, ComponentFactory {

  override fun createDesktopPeer(target: Desktop): DesktopPeer {
    return PDesktopPeer()
  }

  override fun createButton(target: Button): ButtonPeer {
    return PButtonPeer(target)
  }

  override fun createTextField(target: TextField): TextFieldPeer {
    return PTextFieldPeer(target)
  }

  override fun createLabel(target: Label): LabelPeer {
    return PLabelPeer(target)
  }

  override fun createList(target: List): ListPeer {
    return PListPeer(target)
  }

  override fun createCheckbox(target: Checkbox): CheckboxPeer {
    return PCheckboxPeer(target)
  }

  override fun createScrollbar(target: Scrollbar): ScrollbarPeer {
    return PScrollbarPeer(target)
  }

  override fun createScrollPane(target: ScrollPane): ScrollPanePeer {
    return PScrollPanePeer(target)
  }

  override fun createTextArea(target: TextArea): TextAreaPeer {
    return PTextAreaPeer(target)
  }

  override fun createChoice(target: Choice): ChoicePeer {
    return PChoicePeer(target)
  }

  override fun createFrame(target: Frame): FramePeer {
    return PFramePeer(target)
  }

  override fun createCanvas(target: Canvas): CanvasPeer {
    return PCanvasPeer(target)
  }

  override fun createPanel(target: Panel): PanelPeer {
    return PPanelPeer(target)
  }

  override fun createWindow(target: Window): WindowPeer {
    return PWindowPeer(target)
  }

  override fun createDialog(target: Dialog): DialogPeer {
    return PDialogPeer(target)
  }

  override fun createMenuBar(target: MenuBar): MenuBarPeer {
    return PMenuBarPeer()
  }

  override fun createMenu(target: Menu): MenuPeer {
    return PMenuPeer()
  }

  override fun createPopupMenu(target: PopupMenu): PopupMenuPeer {
    return PPopupMenuPeer()
  }

  override fun createMenuItem(target: MenuItem): MenuItemPeer {
    return PMenuItemPeer()
  }

  override fun createFileDialog(target: FileDialog): FileDialogPeer {
    return PFileDialogPeer(target)
  }

  override fun createCheckboxMenuItem(target: CheckboxMenuItem): CheckboxMenuItemPeer {
    return PCheckboxMenuItemPeer()
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

  override fun mapInputMethodHighlight(highlight: InputMethodHighlight): Map<TextAttribute, *>? {
    return PInputMethod.mapInputMethodHighlight(highlight)
  }

  override fun getKeyboardFocusManagerPeer(): KeyboardFocusManagerPeer {
    return PKeyboardFocusManagerPeer
  }

  override fun getMouseInfoPeer(): MouseInfoPeer = PMouseInfoPeer

  private fun getImageFromHash(filename: String): Image? {
    synchronized(imgCache) {
      var img: Image? = imgCache[filename] as Image?

      if (img == null) {
        try {
          img = createImage(FileImageSource(filename))
          imgCache[filename] = img
        }
        catch (e: Exception) {
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
        catch (e: Exception) {
        }
      }

      return img
    }
  }

  companion object {

    private val imgCache = SoftCache()  // todo

    val systemEventQueueImplPP: EventQueue
      get() = AppContext.getAppContext().get(AppContext.EVENT_QUEUE_KEY) as EventQueue
  }
}
