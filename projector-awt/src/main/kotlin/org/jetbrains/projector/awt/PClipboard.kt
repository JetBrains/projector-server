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

  fun putContentsWithoutLastContentsUpdate(contents: Transferable) {
    super.setContents(contents, null)  // owner=null is needed to reset ownership of other owners if they persist
  }

  override fun setContents(contents: Transferable?, owner: ClipboardOwner?) {
    super.setContents(contents, owner)

    synchronized(lastContentsLock) {
      lastContents = contents
    }
  }
}
