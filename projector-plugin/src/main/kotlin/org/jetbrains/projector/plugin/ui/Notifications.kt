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

package org.jetbrains.projector.plugin.ui

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItMessage
import com.intellij.ui.awt.RelativePoint


private fun getNotificationGroup(): NotificationGroup? {
  val cls = NotificationGroup::class.java

  val constr = try {
    cls.getConstructor(String::class.java, NotificationDisplayType::class.java, Boolean::class.java)
  }
  catch (e: NoSuchMethodException) {
    return null
  }
  catch (e: SecurityException) {
    return null
  }

  return try {
    constr.newInstance("projector.notification.group", NotificationDisplayType.STICKY_BALLOON, true) as NotificationGroup
  }
  catch (e: ReflectiveOperationException) {
    null
  }
  catch (e: RuntimeException) {
    null
  }
}

fun displayNotification(title: String, subtitle: String, content: String) {
  val msg = getNotificationGroup()?.createNotification(content, NotificationType.INFORMATION)
  msg?.setTitle(title, subtitle)
  msg?.notify(null)
}
