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
package org.jetbrains.projector.agent

import org.jetbrains.projector.server.ProjectorServer.Companion.ENABLE_PROPERTY_NAME

internal fun setupAgentSystemProperties() {
  // Setting these properties as run arguments isn't enough because they can be overwritten by JVM
  System.setProperty(ENABLE_PROPERTY_NAME, true.toString())
  System.setProperty("swing.bufferPerWindow", false.toString())
  System.setProperty("swing.volatileImageBufferEnabled", false.toString())
}

internal fun setupAgentSingletons() {
  //setupFontManager()
  //setupRepaintManager()
}
