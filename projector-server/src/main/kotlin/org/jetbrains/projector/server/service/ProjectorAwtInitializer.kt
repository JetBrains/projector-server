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
package org.jetbrains.projector.server.service

import org.jetbrains.projector.awt.service.Defaults
import org.jetbrains.projector.awt.service.DrawEventQueue
import org.jetbrains.projector.awt.service.FontProvider
import org.jetbrains.projector.awt.service.ImageCacher
import org.jetbrains.projector.common.protocol.toClient.ServerDrawCommandsEvent
import org.jetbrains.projector.server.core.convert.toClient.toColor
import org.jetbrains.projector.server.core.convert.toClient.toStroke
import org.jetbrains.projector.common.misc.Defaults as CommonDefaults

object ProjectorAwtInitializer {

  fun initProjectorAwt() {
    DrawEventQueue.createOffScreen = {
      ProjectorDrawEventQueue(
        ServerDrawCommandsEvent.Target.Offscreen(
          pVolatileImageId = it.pVolatileImageId,
          width = it.width,
          height = it.height
        )
      )
    }
    DrawEventQueue.createOnScreen = { ProjectorDrawEventQueue(ServerDrawCommandsEvent.Target.Onscreen(it.windowId)) }

    FontProvider.instance = ProjectorFontProvider

    ImageCacher.instance = ProjectorImageCacher
  }

  fun initDefaults() {
    Defaults.BACKGROUND_COLOR_ARGB = CommonDefaults.BACKGROUND_COLOR_ARGB.toColor()
    Defaults.FOREGROUND_COLOR_ARGB = CommonDefaults.FOREGROUND_COLOR_ARGB.toColor()
    Defaults.STROKE = CommonDefaults.STROKE.toStroke()
  }
}
