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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.agent

import org.jetbrains.projector.awt.image.toList
import org.jetbrains.projector.awt.service.ImageCacher
import org.jetbrains.projector.common.misc.Do
import org.jetbrains.projector.common.protocol.data.*
import org.jetbrains.projector.common.protocol.data.Point
import org.jetbrains.projector.common.protocol.toClient.*
import org.jetbrains.projector.server.util.*
import sun.font.FontDesignMetrics
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import javax.swing.UIManager

internal object CommandsHandler {

  fun isSupportedCommand(commandName: String) = commandName in commandsMap

  fun createServerWindowEvents(methodName: String, args: Array<Any?>, g: GraphicsState): List<ServerWindowEvent> {
    return commandsMap[methodName]?.invoke(args, g) ?: emptyList()
  }

  private fun extractTextAntiAliasingHint(hints: RenderingHints?) = hints?.get(RenderingHints.KEY_TEXT_ANTIALIASING)
                                                                    ?: UIManager.getDefaults()[RenderingHints.KEY_TEXT_ANTIALIASING]
                                                                    ?: RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT

  private fun extractFractionalMetricsHint(hints: RenderingHints?) = hints?.get(RenderingHints.KEY_FRACTIONALMETRICS)
                                                                     ?: UIManager.getDefaults()[RenderingHints.KEY_FRACTIONALMETRICS]
                                                                     ?: RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT

  private val AffineTransform.withoutTranslation
    get() = AffineTransform(this).apply {
      setTransform(scaleX, shearY, shearX, scaleY, 0.0, 0.0)
    }

  private fun createPaintValue(paint: Paint): PaintValue = with(paint) {
    when (this) {
      is Color -> PaintValue.Color(rgb)
      is MultipleGradientPaint -> PaintValue.Unknown("MultipleGradientPaint, maybe split to Linear and Radial")
      is TexturePaint -> PaintValue.Unknown("TexturePaint")
      is GradientPaint -> PaintValue.Gradient(
        p1 = point1.toPoint(),
        p2 = point2.toPoint(),
        argb1 = color1.rgb,
        argb2 = color2.rgb
      )

      else -> PaintValue.Unknown(this::class.qualifiedName.toString())
    }
  }

  private fun createSetClipEvent(identitySpaceClip: Shape?): ServerWindowStateEvent = ServerSetClipEvent(
    with(identitySpaceClip) {
      when (this) {
        null -> null
        is Rectangle2D -> CommonRectangle(x, y, width, height)
        else -> this.toCommonPath()
      }
    }
  )

  private fun createSetStrokeDataEvent(stroke: Stroke): ServerWindowStateEvent = with(stroke) {
    when (this) {
      is BasicStroke -> ServerSetStrokeEvent(this.toBasicStrokeData())
      else -> ServerSetUnknownStrokeEvent(this::class.qualifiedName.toString())
    }
  }

  private fun extractFontRenderingContextFromGraphicsState(g: GraphicsState): FontRenderContext {
    return FontRenderContext(
      g.transform.withoutTranslation,
      extractTextAntiAliasingHint(g.hints),
      extractFractionalMetricsHint(g.hints)
    )
  }

  private fun convertToServerWindowEvent(
    paintEvent: ServerWindowPaintEvent,
    g: GraphicsState,
  ): List<ServerWindowEvent> = Do exhaustive when (paintEvent) {
    is ServerWindowToDoPaintEvent -> listOf(paintEvent)

    is ServerPaintOvalEvent,
    is ServerPaintRoundRectEvent,
    is ServerPaintRectEvent,
    is ServerDrawLineEvent,
    is ServerDrawPolylineEvent,
    is ServerPaintPolygonEvent,
    is ServerPaintPathEvent,
    -> listOf(
      createSetClipEvent(g.clip),
      ServerSetTransformEvent(tx = g.transform.toList()),
      createSetStrokeDataEvent(g.stroke),
      ServerSetPaintEvent(createPaintValue(g.paint)),
      ServerSetCompositeEvent(g.composite.toCommonComposite()),
      paintEvent
    )

    is ServerDrawImageEvent,
    is ServerCopyAreaEvent,
    -> listOf(
      createSetClipEvent(g.clip),
      ServerSetTransformEvent(tx = g.transform.toList()),
      ServerSetCompositeEvent(g.composite.toCommonComposite()),
      paintEvent
    )

    is ServerDrawStringEvent -> listOf(
      createSetClipEvent(g.clip),
      ServerSetTransformEvent(tx = g.transform.toList()),
      g.font.let { ServerSetFontEvent(fontId = FontCacher.getId(it), fontSize = it.size) },
      ServerSetPaintEvent(createPaintValue(g.paint)),
      ServerSetCompositeEvent(g.composite.toCommonComposite()),
      paintEvent
    )
  }

  private fun paintRectCommand(
    paintType: PaintType,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    g: GraphicsState,
  ): List<ServerWindowEvent> {
    val event = ServerPaintRectEvent(paintType, x, y, width, height)
    return convertToServerWindowEvent(event, g)
  }

  private fun paintShape(paintType: PaintType, shape: Shape, g: GraphicsState): List<ServerWindowEvent> {
    return when (shape) {
      is Rectangle2D -> paintRectCommand(paintType, shape.x, shape.y, shape.width, shape.height, g)

      else -> convertToServerWindowEvent(ServerPaintPathEvent(paintType, shape.toCommonPath()), g)
    }
  }

  private fun draw(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return paintShape(paintType = PaintType.DRAW, shape = args[0] as Shape, g = graphicsState)
  }

  @Suppress("UNUSED_PARAMETER")  // todo: use parameter
  private fun drawRenderedImage(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return convertToServerWindowEvent(paintEvent = ServerDrawRenderedImageEvent, g = graphicsState)
  }

  @Suppress("UNUSED_PARAMETER")  // todo: use parameter
  private fun drawRenderableImage(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return convertToServerWindowEvent(paintEvent = ServerDrawRenderableImageEvent, g = graphicsState)
  }

  private fun drawString(str: String, x: Double, y: Double, g: GraphicsState): List<ServerWindowEvent> {
    val metrics = FontDesignMetrics.getMetrics(g.font, extractFontRenderingContextFromGraphicsState(g))
    val desiredWidth = metrics.stringWidth(str)
    val event = ServerDrawStringEvent(str = str, x = x, y = y, desiredWidth = desiredWidth.toDouble())
    return convertToServerWindowEvent(paintEvent = event, g = g)
  }

  private fun drawStringSII(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return drawString(
      str = args[0] as String,
      x = args[1].asDouble(),
      y = args[2].asDouble(),
      g = graphicsState
    )
  }

  private fun drawStringSFF(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return drawString(
      str = args[0] as String,
      x = args[1].asDouble(),
      y = args[2].asDouble(),
      g = graphicsState
    )
  }

  private fun drawChars(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return drawString(
      str = String(
        chars = args[0] as CharArray,
        offset = args[1] as Int,
        length = args[2] as Int
      ),
      x = args[3].asDouble(),
      y = args[4].asDouble(),
      g = graphicsState
    )
  }

  private fun fill(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return paintShape(paintType = PaintType.FILL, shape = args[0] as Shape, g = graphicsState)
  }

  private fun copyArea(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return convertToServerWindowEvent(
      paintEvent = ServerCopyAreaEvent(
        x = args[0] as Int,
        y = args[1] as Int,
        width = args[2] as Int,
        height = args[3] as Int,
        dx = args[4] as Int,
        dy = args[5] as Int
      ),
      g = graphicsState)
  }

  private fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, g: GraphicsState): List<ServerWindowEvent> {
    val event = ServerDrawLineEvent(x1, y1, x2, y2)
    return convertToServerWindowEvent(event, g)
  }

  private fun drawLine(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return drawLine(
      x1 = args[0] as Int,
      y1 = args[1] as Int,
      x2 = args[2] as Int,
      y2 = args[3] as Int,
      g = graphicsState
    )
  }

  // Took from java.awt.Graphics.drawRect
  private fun drawRect(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    val x: Int = args[0] as Int
    val y: Int = args[1] as Int
    val width: Int = args[2] as Int
    val height: Int = args[3] as Int
    val g = graphicsState

    if (width < 0 || height < 0) {
      return emptyList()
    }

    val events = ArrayList<ServerWindowEvent>()

    if (height == 0 || width == 0) {
      drawLine(x, y, x + width, y + height, g).run(events::addAll)
    }
    else {
      drawLine(x, y, x + width - 1, y, g).run(events::addAll)
      drawLine(x + width, y, x + width, y + height - 1, g).run(events::addAll)
      drawLine(x + width, y + height, x + 1, y + height, g).run(events::addAll)
      drawLine(x, y + height, x, y + 1, g).run(events::addAll)
    }

    return events
  }

  private fun fillRect(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return paintRectCommand(
      paintType = PaintType.FILL,
      x = args[0].asDouble(),
      y = args[1].asDouble(),
      width = args[2].asDouble(),
      height = args[3].asDouble(),
      g = graphicsState
    )
  }

  private fun clearRect(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return paintRectCommand(
      paintType = PaintType.FILL,
      x = args[0].asDouble(),
      y = args[1].asDouble(),
      width = args[2].asDouble(),
      height = args[3].asDouble(),
      g = graphicsState.copy(composite = AlphaComposite.Src, paint = graphicsState.background)
    )
  }

  private fun drawRoundRect(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return convertToServerWindowEvent(
      ServerPaintRoundRectEvent(
        paintType = PaintType.DRAW,
        x = args[0] as Int,
        y = args[1] as Int,
        width = args[2] as Int,
        height = args[3] as Int,
        arcWidth = args[4] as Int,
        arcHeight = args[5] as Int
      ),
      graphicsState
    )
  }

  private fun fillRoundRect(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    val event = ServerPaintRoundRectEvent(
      paintType = PaintType.FILL,
      x = args[0] as Int,
      y = args[1] as Int,
      width = args[2] as Int,
      height = args[3] as Int,
      arcWidth = args[4] as Int,
      arcHeight = args[5] as Int
    )

    return convertToServerWindowEvent(event, graphicsState)
  }

  private fun drawOval(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {

    return convertToServerWindowEvent(
      paintEvent = ServerPaintOvalEvent(
        paintType = PaintType.DRAW,
        x = args[0] as Int,
        y = args[1] as Int,
        width = args[2] as Int,
        height = args[3] as Int
      ),
      g = graphicsState
    )
  }

  private fun fillOval(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return convertToServerWindowEvent(
      paintEvent = ServerPaintOvalEvent(
        paintType = PaintType.FILL,
        x = args[0] as Int,
        y = args[1] as Int,
        width = args[2] as Int,
        height = args[3] as Int
      ),
      g = graphicsState
    )
  }

  private fun drawArc(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return convertToServerWindowEvent(
      paintEvent = ServerPaintArcEvent(
        paintType = PaintType.DRAW,
        x = args[0] as Int,
        y = args[1] as Int,
        width = args[2] as Int,
        height = args[3] as Int,
        startAngle = args[4] as Int,
        arcAngle = args[5] as Int
      ),
      g = graphicsState
    )
  }

  private fun fillArc(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return convertToServerWindowEvent(
      paintEvent = ServerPaintArcEvent(
        paintType = PaintType.FILL,
        x = args[0] as Int,
        y = args[1] as Int,
        width = args[2] as Int,
        height = args[3] as Int,
        startAngle = args[4] as Int,
        arcAngle = args[5] as Int
      ),
      g = graphicsState
    )
  }

  private fun drawPolyline(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    val xPoints = args[0] as IntArray
    val yPoints = args[1] as IntArray
    val nPoints = args[2] as Int

    val event = ServerDrawPolylineEvent(
      xPoints.take(nPoints).zip(yPoints.take(nPoints)) { x, y -> Point(x.toDouble(), y.toDouble()) }
    )
    return convertToServerWindowEvent(event, graphicsState)
  }

  private fun paintPolygon(
    paintType: PaintType,
    xPoints: IntArray,
    yPoints: IntArray,
    nPoints: Int,
    g: GraphicsState,
  ): List<ServerWindowEvent> {
    val event = ServerPaintPolygonEvent(
      paintType,
      xPoints.take(nPoints).zip(yPoints.take(nPoints)) { x, y -> Point(x.toDouble(), y.toDouble()) }
    )
    return convertToServerWindowEvent(event, g)
  }

  private fun drawPolygon(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return paintPolygon(
      paintType = PaintType.DRAW,
      xPoints = args[0] as IntArray,
      yPoints = args[1] as IntArray,
      nPoints = args[2] as Int,
      g = graphicsState
    )
  }

  private fun fillPolygon(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return paintPolygon(
      paintType = PaintType.FILL,
      xPoints = args[0] as IntArray,
      yPoints = args[1] as IntArray,
      nPoints = args[2] as Int,
      g = graphicsState
    )
  }

  private fun extractImage(img: Image?, imageEventInfo: ImageEventInfo, methodName: String, g: GraphicsState): List<ServerWindowEvent> {
    if (img == null) {
      return emptyList()
    }

    return convertToServerWindowEvent(
      ServerDrawImageEvent(imageId = ImageCacher.instance.getImageId(img, methodName) as ImageId, imageEventInfo = imageEventInfo), g)
  }

  private fun drawImage0(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return extractImage(
      img = args[0] as Image?,
      imageEventInfo = ImageEventInfo.Transformed((args[1] as AffineTransform).toList()),
      methodName = "drawImage(img, xform, obs)",
      g = graphicsState
    )
  }

  private fun drawImage1(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {

    return extractImage(
      img = (args[1] as BufferedImageOp).filter(args[0] as BufferedImage? ?: return emptyList(), null),
      imageEventInfo = AffineTransform(1.0, 0.0, 0.0, 1.0, args[2].asDouble(), args[3].asDouble())
        .toList()
        .run(ImageEventInfo::Transformed),
      methodName = "drawImage(img, xform, obs)",
      g = graphicsState
    )
  }

  private fun drawImage2(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return extractImage(
      img = args[0] as Image?,
      imageEventInfo = ImageEventInfo.Xy(
        x = args[1] as Int,
        y = args[2] as Int,
        argbBackgroundColor = (args[3] as Color?)?.rgb),
      methodName = "drawImage(img, x, y, bgcolor, observer)",
      g = graphicsState
    )
  }

  private fun drawImage3(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {

    return extractImage(
      img = args[0] as Image?,
      imageEventInfo = ImageEventInfo.XyWh(
        x = args[1] as Int,
        y = args[2] as Int,
        width = args[3] as Int,
        height = args[4] as Int,
        argbBackgroundColor = (args[5] as Color?)?.rgb
      ),
      methodName = "drawImage(img, x, y, w, h, bgcolor, observer)",
      g = graphicsState
    )
  }

  private fun drawImage4(args: Array<Any?>, graphicsState: GraphicsState): List<ServerWindowEvent> {
    return extractImage(
      img = args[0] as Image?,
      imageEventInfo = ImageEventInfo.Ds(
        dx1 = args[1] as Int,
        dy1 = args[2] as Int,
        dx2 = args[3] as Int,
        dy2 = args[4] as Int,
        sx1 = args[5] as Int,
        sy1 = args[6] as Int,
        sx2 = args[7] as Int,
        sy2 = args[8] as Int,
        argbBackgroundColor = (args[9] as Color?)?.rgb
      ),
      methodName = "drawImage(img, d..., s..., bgcolor, observer)",
      g = graphicsState
    )
  }

  private val commandsMap: Map<String, (Array<Any?>, GraphicsState) -> List<ServerWindowEvent>> = mapOf(
    "sun.java2d.SunGraphics2D.draw(java.awt.Shape)" to CommandsHandler::draw,
    "sun.java2d.SunGraphics2D.drawRenderedImage(java.awt.image.RenderedImage,java.awt.geom.AffineTransform)" to CommandsHandler::drawRenderedImage,
    "sun.java2d.SunGraphics2D.drawRenderableImage(java.awt.image.renderable.RenderableImage,java.awt.geom.AffineTransform)" to CommandsHandler::drawRenderableImage,
    "sun.java2d.SunGraphics2D.drawString(java.lang.String,int,int)" to CommandsHandler::drawStringSII,
    "sun.java2d.SunGraphics2D.drawString(java.lang.String,float,float)" to CommandsHandler::drawStringSFF,
    "sun.java2d.SunGraphics2D.drawChars(char[],int,int,int,int)" to CommandsHandler::drawChars,
    "sun.java2d.SunGraphics2D.fill(java.awt.Shape)" to CommandsHandler::fill,
    "sun.java2d.SunGraphics2D.copyArea(int,int,int,int,int,int)" to CommandsHandler::copyArea,
    "sun.java2d.SunGraphics2D.drawLine(int,int,int,int)" to CommandsHandler::drawLine,
    "sun.java2d.SunGraphics2D.drawRect(int,int,int,int)" to CommandsHandler::drawRect,
    "sun.java2d.SunGraphics2D.fillRect(int,int,int,int)" to CommandsHandler::fillRect,
    "sun.java2d.SunGraphics2D.clearRect(int,int,int,int)" to CommandsHandler::clearRect,
    "sun.java2d.SunGraphics2D.drawRoundRect(int,int,int,int,int,int)" to CommandsHandler::drawRoundRect,
    "sun.java2d.SunGraphics2D.fillRoundRect(int,int,int,int,int,int)" to CommandsHandler::fillRoundRect,
    "sun.java2d.SunGraphics2D.drawOval(int,int,int,int)" to CommandsHandler::drawOval,
    "sun.java2d.SunGraphics2D.fillOval(int,int,int,int)" to CommandsHandler::fillOval,
    "sun.java2d.SunGraphics2D.drawArc(int,int,int,int,int,int)" to CommandsHandler::drawArc,
    "sun.java2d.SunGraphics2D.fillArc(int,int,int,int,int,int)" to CommandsHandler::fillArc,
    "sun.java2d.SunGraphics2D.drawPolyline(int[],int[],int)" to CommandsHandler::drawPolyline,
    "sun.java2d.SunGraphics2D.drawPolygon(int[],int[],int)" to CommandsHandler::drawPolygon,
    "sun.java2d.SunGraphics2D.fillPolygon(int[],int[],int)" to CommandsHandler::fillPolygon,
    "sun.java2d.SunGraphics2D.drawImage(java.awt.Image,java.awt.geom.AffineTransform,java.awt.image.ImageObserver)" to CommandsHandler::drawImage0,
    "sun.java2d.SunGraphics2D.drawImage(java.awt.image.BufferedImage,java.awt.image.BufferedImageOp,int,int)" to CommandsHandler::drawImage1,
    "sun.java2d.SunGraphics2D.drawImage(java.awt.Image,int,int,java.awt.Color,java.awt.image.ImageObserver)" to CommandsHandler::drawImage2,
    "sun.java2d.SunGraphics2D.drawImage(java.awt.Image,int,int,int,int,java.awt.Color,java.awt.image.ImageObserver)" to CommandsHandler::drawImage3,
    "sun.java2d.SunGraphics2D.drawImage(java.awt.Image,int,int,int,int,int,int,int,int,java.awt.Color,java.awt.image.ImageObserver)" to CommandsHandler::drawImage4
  )

  private fun Any?.asDouble(): Double = (this as Number).toDouble()
}
