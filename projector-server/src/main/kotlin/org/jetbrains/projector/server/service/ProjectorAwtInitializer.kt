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
package org.jetbrains.projector.server.service

import org.jetbrains.projector.awt.service.Defaults
import org.jetbrains.projector.awt.service.DrawEventQueue
import org.jetbrains.projector.awt.service.FontProvider
import org.jetbrains.projector.awt.service.ImageCacher
import org.jetbrains.projector.common.protocol.toClient.ServerDrawCommandsEvent
import org.jetbrains.projector.server.util.toColor
import org.jetbrains.projector.server.util.toStroke
import org.jetbrains.projector.awt.service.Logger as AwtLogger
import org.jetbrains.projector.common.misc.Defaults as CommonDefaults

object ProjectorAwtInitializer {

  fun initProjectorAwt() {
    DrawEventQueue.createOffScreen = {
      ProjectorDrawEventQueue.create(
        ServerDrawCommandsEvent.Target.Offscreen(
          pVolatileImageId = it.pVolatileImageId,
          width = it.width,
          height = it.height
        )
      )
    }
    DrawEventQueue.createOnScreen = { ProjectorDrawEventQueue.create(ServerDrawCommandsEvent.Target.Onscreen(it.windowId)) }

    FontProvider.instance = ProjectorFontProvider

    ImageCacher.instance = ProjectorImageCacher

    AwtLogger.factory = { ProjectorLogger(it.simpleName) }
  }

  fun initDefaults() {
    Defaults.BACKGROUND_COLOR_ARGB = CommonDefaults.BACKGROUND_COLOR_ARGB.toColor()
    Defaults.FOREGROUND_COLOR_ARGB = CommonDefaults.FOREGROUND_COLOR_ARGB.toColor()
    Defaults.STROKE = CommonDefaults.STROKE.toStroke()
  }
}
