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
@file:UseProjectorLoader

package org.jetbrains.projector.server.idea

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.impl.NotificationsConfigurationImpl
import org.jetbrains.projector.util.loading.UseProjectorLoader
import org.jetbrains.projector.util.loading.state.IdeaState
import org.jetbrains.projector.util.loading.state.whenOccurred

fun forbidUpdates() {
  forbidPlatformUpdates()
  IdeaState.CONFIGURATION_STORE_INITIALIZED.whenOccurred("Forbid platform updates and plugin update notifications") {
    forbidPluginsUpdatesNotifications()
  }
}

private const val PLUGINS_UPDATES_GROUP = "Plugins updates"
private const val IDE_AND_PLUGINS_UPDATES_GROUP = "IDE and Plugin Updates"

private fun forbidPluginsUpdatesNotifications() {
  with(NotificationsConfigurationImpl.getInstanceImpl()) {
    changeSettings(PLUGINS_UPDATES_GROUP,
                   NotificationDisplayType.NONE,
                   false,
                   false)

    changeSettings(IDE_AND_PLUGINS_UPDATES_GROUP,
                   NotificationDisplayType.NONE,
                   false,
                   false)

  }
}

private const val NO_PLATFORM_UPDATE_KEY = "ide.no.platform.update"

private fun forbidPlatformUpdates() {
  System.setProperty(NO_PLATFORM_UPDATE_KEY, "Projector")
}
