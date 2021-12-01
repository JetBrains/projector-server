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

package org.jetbrains.projector.awt.peer

import org.jetbrains.projector.util.logging.Logger
import java.awt.Desktop.Action
import java.awt.peer.DesktopPeer
import java.io.File
import java.net.URI

class PDesktopPeer : DesktopPeer {

  override fun isSupported(action: Action): Boolean {
    return action in setOf(Action.OPEN, Action.EDIT, Action.PRINT, Action.MAIL, Action.BROWSE)
  }

  override fun open(file: File) {
    logger.debug { "ignored open $file" }
  }

  override fun edit(file: File) {
    logger.debug { "ignored edit $file" }
  }

  override fun print(file: File) {
    logger.debug { "ignored print $file" }
  }

  override fun mail(mailtoURL: URI) {
    logger.debug { "ignored mail $mailtoURL" }
  }

  override fun browse(url: URI) {
    browseUriCallback?.invoke(url.toASCIIString()) ?: logger.debug { "ignored browse $url" }
  }

  companion object {

    private val logger = Logger(PDesktopPeer::class.simpleName!!)

    var browseUriCallback: ((link: String) -> Unit)? = null
  }
}
