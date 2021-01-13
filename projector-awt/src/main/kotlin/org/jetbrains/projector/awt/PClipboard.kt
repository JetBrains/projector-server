/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2021 JetBrains s.r.o.
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
package org.jetbrains.projector.awt

import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.Transferable

object PClipboard : Clipboard("System") {

  private var lastContents: Transferable? = null

  private val lastContentsLock = Unit

  fun extractLastContents(): Transferable? {
    synchronized(lastContentsLock) {
      return lastContents.also { lastContents = null }
    }
  }

  fun putContents(contents: Transferable) {
    super.setContents(contents, null)  // owner=null is needed to reset ownership of other owners if they persist
  }

  override fun setContents(contents: Transferable?, owner: ClipboardOwner?) {
    super.setContents(contents, owner)

    synchronized(lastContentsLock) {
      lastContents = contents
    }
  }
}
