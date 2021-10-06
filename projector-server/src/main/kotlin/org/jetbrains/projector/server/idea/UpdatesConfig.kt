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
package org.jetbrains.projector.server.idea

import org.jetbrains.projector.server.core.ij.invokeWhenIdeaIsInitialized

fun configureUpdates(isAgent: Boolean) {
  if (!isAgent) {
    forbidPlatformUpdates()
    invokeWhenIdeaIsInitialized("Forbid platform updates and plugin update notifications",
                                null,
                                null) {
      forbidPluginsUpdatesNotifications(it)
    }
  }
}

private const val PLUGINS_UPDATES_GROUP = "Plugins updates"

private fun forbidPluginsUpdatesNotifications(ideaClassLoader: ClassLoader) {
  try {
    val notificationConfigImplClass = ideaClassLoader.loadClass("com.intellij.notification.impl.NotificationsConfigurationImpl")
    val displayTypeClass = ideaClassLoader.loadClass("com.intellij.notification.NotificationDisplayType")
    @Suppress("UNCHECKED_CAST")
    val displayTypeValueNone = (displayTypeClass.enumConstants as Array<Enum<*>>).first { it.name == "NONE" }

    val getInstanceMethod = notificationConfigImplClass.getMethod("getInstanceImpl")
    val changeSettingsMethod = notificationConfigImplClass.getMethod("changeSettings",
                                                                     String::class.java,
                                                                     displayTypeClass,
                                                                     Boolean::class.java,
                                                                     Boolean::class.java)

    val config = getInstanceMethod.invoke(null)
    changeSettingsMethod.invoke(config, PLUGINS_UPDATES_GROUP, displayTypeValueNone, false, false)
  }
  catch (e: ClassNotFoundException) {

  }
  catch (e: NoSuchMethodException) {

  }
}

private const val NO_PLATFORM_UPDATE_KEY = "ide.no.platform.update"

private fun forbidPlatformUpdates() {
  System.setProperty(NO_PLATFORM_UPDATE_KEY, "Projector")
}
