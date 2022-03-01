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
package org.jetbrains.projector.server.service

import org.jetbrains.projector.awt.data.AwtImageInfo
import org.jetbrains.projector.awt.data.AwtPaintType
import org.jetbrains.projector.awt.service.DrawEventQueue
import org.jetbrains.projector.common.protocol.data.ImageId
import org.jetbrains.projector.common.protocol.data.Point
import org.jetbrains.projector.common.protocol.toClient.*
import org.jetbrains.projector.server.ProjectorServer
import org.jetbrains.projector.server.core.convert.toClient.*
import org.jetbrains.projector.server.core.util.SizeAware
import org.jetbrains.projector.server.util.FontCacher
import org.jetbrains.projector.server.util.toImageEventInfo
import org.jetbrains.projector.server.util.toPaintType
import org.jetbrains.projector.util.logging.Logger
import java.awt.*
import java.awt.font.TextAttribute
import java.util.concurrent.ConcurrentLinkedQueue

class ProjectorDrawEventQueue(private val target: ServerDrawCommandsEvent.Target) : DrawEventQueue {

  override fun buildCommand(): DrawEventQueue.CommandBuilder = CommandBuilder(target)

  private class CommandBuilder(val target: ServerDrawCommandsEvent.Target) : DrawEventQueue.CommandBuilder {

    private val events = mutableListOf<ServerWindowEvent>()

    override fun setClip(identitySpaceClip: Shape?): DrawEventQueue.CommandBuilder {
      events.add(createSetClipEvent(identitySpaceClip))
      return this
    }

    override fun setTransform(tx: List<Double>): DrawEventQueue.CommandBuilder {
      events.add(ServerSetTransformEvent(tx))
      return this
    }

    override fun setStroke(stroke: Stroke): DrawEventQueue.CommandBuilder {
      events.add(stroke.toSetStrokeEvent())
      return this
    }

    override fun setPaint(paint: Paint): DrawEventQueue.CommandBuilder {
      events.add(ServerSetPaintEvent(paint.toPaintValue()))
      return this
    }

    override fun setComposite(composite: Composite): DrawEventQueue.CommandBuilder {
      events.add(ServerSetCompositeEvent(composite.toCommonComposite()))
      return this
    }

    override fun setFont(font: Font): DrawEventQueue.CommandBuilder {
      events.add(ServerSetFontEvent(
        fontId = FontCacher.getId(font),
        fontSize = font.size,
        ligaturesOn = ((font.attributes[TextAttribute.LIGATURES] as Int?) ?: 0) > 0))
      return this
    }

    private fun build() {
      commands.add(target to events)
      // asReversed: optimization. We expect that the last element is ServerWindowPaintEvent most of the time
      if (events.asReversed().none { it is ServerWindowPaintEvent }) {
        logger.info { "Performance problem detected: draw commands are added but they only change drawing state but don't draw anything" }
      }
    }

    override fun drawRenderedImage() {
      events.add(ServerDrawRenderedImageEvent)
      build()
    }

    override fun drawRenderableImage() {
      events.add(ServerDrawRenderableImageEvent)
      build()
    }

    override fun drawString(string: String, x: Double, y: Double, desiredWidth: Double) {
      events.add(ServerDrawStringEvent(string, x = x, y = y, desiredWidth = desiredWidth))
      build()
    }

    override fun copyArea(x: Int, y: Int, width: Int, height: Int, dx: Int, dy: Int) {
      events.add(ServerCopyAreaEvent(x = x, y = y, width = width, height = height, dx = dx, dy = dy))
      build()
    }

    override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
      events.add(ServerDrawLineEvent(x1 = x1, y1 = y1, x2 = x2, y2 = y2))
      build()
    }

    override fun paintRect(paintType: AwtPaintType, x: Double, y: Double, width: Double, height: Double) {
      events.add(ServerPaintRectEvent(paintType.toPaintType(), x = x, y = y, width = width, height = height))
      build()
    }

    override fun paintRoundRect(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
      events.add(
        ServerPaintRoundRectEvent(
          paintType.toPaintType(),
          x = x, y = y, width = width, height = height, arcWidth = arcWidth, arcHeight = arcHeight
        )
      )
      build()
    }

    override fun paintOval(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int) {
      events.add(ServerPaintOvalEvent(paintType.toPaintType(), x = x, y = y, width = width, height = height))
      build()
    }

    override fun paintArc(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
      events.add(
        ServerPaintArcEvent(
          paintType.toPaintType(),
          x = x, y = y, width = width, height = height, startAngle = startAngle, arcAngle = arcAngle
        )
      )
      build()
    }

    override fun drawPolyline(points: List<Pair<Int, Int>>) {
      events.add(ServerDrawPolylineEvent(points.toPoints()))
      build()
    }

    override fun paintPolygon(paintType: AwtPaintType, points: List<Pair<Int, Int>>) {
      events.add(ServerPaintPolygonEvent(paintType.toPaintType(), points.toPoints()))
      build()
    }

    override fun drawImage(imageId: Any, awtImageInfo: AwtImageInfo) {
      require(imageId is ImageId)
      events.add(ServerDrawImageEvent(imageId, awtImageInfo.toImageEventInfo()))
      build()
    }

    override fun paintPath(paintType: AwtPaintType, path: Shape) {
      events.add(ServerPaintPathEvent(paintType.toPaintType(), path.toCommonPath()))
      build()
    }
  }

  companion object {

    private val logger = Logger<ProjectorDrawEventQueue>()

    private fun List<Pair<Int, Int>>.toPoints() = map { (x, y) -> Point(x.toDouble(), y.toDouble()) }

    val commands by SizeAware(
      ConcurrentLinkedQueue<Pair<ServerDrawCommandsEvent.Target, List<ServerWindowEvent>>>(),
      if (ProjectorServer.ENABLE_BIG_COLLECTIONS_CHECKS) ProjectorServer.BIG_COLLECTIONS_CHECKS_START_SIZE else null,
      logger,
    )
  }
}
