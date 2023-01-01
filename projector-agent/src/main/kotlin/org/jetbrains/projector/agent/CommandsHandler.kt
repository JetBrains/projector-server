/*
 * Copyright (c) 2019-2023, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

package org.jetbrains.projector.agent

import org.jetbrains.projector.awt.data.AwtImageInfo
import org.jetbrains.projector.awt.data.AwtPaintType
import org.jetbrains.projector.awt.image.toList
import org.jetbrains.projector.awt.service.DrawEventQueue
import org.jetbrains.projector.awt.service.ImageCacher
import org.jetbrains.projector.util.logging.Logger
import sun.font.FontDesignMetrics
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.RenderedImage
import javax.swing.UIManager

internal object CommandsHandler {

  fun isSupportedCommand(commandName: String) = commandName in commandsMap

  fun createServerWindowEvents(methodName: String, args: Array<Any?>, g: GraphicsState, drawEventQueue: DrawEventQueue) {
    commandsMap[methodName]?.invoke(args, g, drawEventQueue) ?: logger.error { "Unknown method name: $methodName" }
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

  private fun extractFontRenderingContextFromGraphicsState(g: GraphicsState): FontRenderContext {
    return FontRenderContext(
      g.transform.withoutTranslation,
      extractTextAntiAliasingHint(g.hints),
      extractFractionalMetricsHint(g.hints)
    )
  }

  private inline fun paintPlain(drawEventQueue: DrawEventQueue, crossinline command: DrawEventQueue.CommandBuilder.() -> Unit) {
    drawEventQueue.buildCommand().command()
  }

  private inline fun paintShape(
    g: GraphicsState,
    drawEventQueue: DrawEventQueue,
    crossinline command: DrawEventQueue.CommandBuilder.() -> Unit,
  ) {
    drawEventQueue
      .buildCommand()
      .setClip(identitySpaceClip = g.clip)
      .setTransform(g.transform.toList())
      .setStroke(g.stroke)
      .setPaint(g.paint)
      .setComposite(g.composite)
      .command()
  }

  private inline fun paintArea(
    g: GraphicsState,
    drawEventQueue: DrawEventQueue,
    crossinline command: DrawEventQueue.CommandBuilder.() -> Unit,
  ) {
    drawEventQueue
      .buildCommand()
      .setClip(identitySpaceClip = g.clip)
      .setTransform(g.transform.toList())
      .setComposite(g.composite)
      .command()
  }

  private fun paintString(str: String, x: Double, y: Double, g: GraphicsState, drawEventQueue: DrawEventQueue) {
    if (str.isBlank()) {
      return
    }

    val metrics = FontDesignMetrics.getMetrics(g.font, extractFontRenderingContextFromGraphicsState(g))
    val desiredWidth = metrics.stringWidth(str)

    drawEventQueue
      .buildCommand()
      .setClip(identitySpaceClip = g.clip)
      .setTransform(g.transform.toList())
      .setFont(g.font)
      .setPaint(g.paint)
      .setComposite(g.composite)
      .drawString(str, x = x, y = y, desiredWidth = desiredWidth.toDouble())
  }

  private fun addPaintRectCommand(
    paintType: AwtPaintType,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    graphicsState: GraphicsState,
    drawEventQueue: DrawEventQueue,
  ) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape(graphicsState, drawEventQueue) {
      paintRect(
        paintType = paintType,
        x = x,
        y = y,
        width = width,
        height = height
      )
    }
  }

  private fun paintShape(paintType: AwtPaintType, shape: Shape, g: GraphicsState, drawEventQueue: DrawEventQueue) {
    when (shape) {
      is Rectangle2D -> addPaintRectCommand(paintType, shape.x, shape.y, shape.width, shape.height, g, drawEventQueue)

      else -> paintShape(g, drawEventQueue) { paintPath(paintType, shape) }
    }
  }

  private fun draw(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    paintShape(paintType = AwtPaintType.DRAW, shape = args[0] as Shape, g = graphicsState, drawEventQueue = drawEventQueue)
  }

  private fun drawRenderedImage(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val img = args[0] as RenderedImage
    val xform = args[1] as AffineTransform?

    // xform nullability is required for compatibility, so provide a default (identity) transformation
    val xFormOrDefault = xform ?: AffineTransform()

    // Currently only BufferedImage is supported
    when (img) {
      is BufferedImage -> {
        val info = AwtImageInfo.Transformation(xFormOrDefault.toList())
        extractImage(img, info, "drawRenderedImage(img, xform)", graphicsState, drawEventQueue)
      }
      else -> paintPlain(drawEventQueue) { drawRenderedImage() }
    }
  }

  @Suppress("UNUSED_PARAMETER")  // todo: use parameter
  private fun drawRenderableImage(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    paintPlain(drawEventQueue) { drawRenderableImage() }
  }

  private fun drawString(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val str = args[0] as String
    val x = args[1].asDouble()
    val y = args[2].asDouble()
    paintString(str, x = x, y = y, graphicsState, drawEventQueue)
  }

  private fun drawStringSII(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    drawString(args, graphicsState, drawEventQueue)
  }

  private fun drawStringSFF(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    drawString(args, graphicsState, drawEventQueue)
  }

  // Took from java.awt.Graphics.drawChars
  private fun drawChars(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    paintString(
      str = String(
        chars = args[0] as CharArray,
        offset = args[1] as Int,
        length = args[2] as Int
      ),
      x = args[3].asDouble(),
      y = args[4].asDouble(),
      g = graphicsState,
      drawEventQueue,
    )
  }

  private fun fill(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    paintShape(paintType = AwtPaintType.FILL, shape = args[0] as Shape, g = graphicsState, drawEventQueue)
  }

  private fun copyArea(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0] as Int
    val y = args[1] as Int
    val width = args[2] as Int
    val height = args[3] as Int
    val dx = args[4] as Int
    val dy = args[5] as Int

    paintArea(graphicsState, drawEventQueue) {
      copyArea(
        x = x,
        y = y,
        width = width,
        height = height,
        dx = dx,
        dy = dy
      )
    }
  }

  private fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, g: GraphicsState, drawEventQueue: DrawEventQueue) {
    paintShape(g, drawEventQueue) {
      drawLine(
        x1 = x1,
        y1 = y1,
        x2 = x2,
        y2 = y2
      )
    }
  }

  private fun drawLine(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    drawLine(
      x1 = args[0] as Int,
      y1 = args[1] as Int,
      x2 = args[2] as Int,
      y2 = args[3] as Int,
      g = graphicsState,
      drawEventQueue,
    )
  }

  // Took from java.awt.Graphics.drawRect
  private fun drawRect(args: Array<Any?>, g: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x: Int = args[0] as Int
    val y: Int = args[1] as Int
    val width: Int = args[2] as Int
    val height: Int = args[3] as Int

    if (width < 0 || height < 0) {
      return
    }

    if (height == 0 || width == 0) {
      drawLine(x, y, x + width, y + height, g, drawEventQueue)
    }
    else {
      drawLine(x, y, x + width - 1, y, g, drawEventQueue)
      drawLine(x + width, y, x + width, y + height - 1, g, drawEventQueue)
      drawLine(x + width, y + height, x + 1, y + height, g, drawEventQueue)
      drawLine(x, y + height, x, y + 1, g, drawEventQueue)
    }
  }

  private fun fillRect(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0].asDouble()
    val y = args[1].asDouble()
    val width = args[2].asDouble()
    val height = args[3].asDouble()

    addPaintRectCommand(AwtPaintType.FILL, x, y, width, height, graphicsState, drawEventQueue)
  }

  private fun clearRect(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0].asDouble()
    val y = args[1].asDouble()
    val width = args[2].asDouble()
    val height = args[3].asDouble()
    addPaintRectCommand(
      AwtPaintType.FILL, x, y, width, height,
      graphicsState.copy(composite = AlphaComposite.Src, paint = graphicsState.background), drawEventQueue,
    )
  }

  private fun paintRoundRect(
    paintType: AwtPaintType,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    arcWidth: Int,
    arcHeight: Int,
    graphicsState: GraphicsState,
    drawEventQueue: DrawEventQueue,
  ) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape(graphicsState, drawEventQueue) {
      paintRoundRect(
        paintType = paintType,
        x = x,
        y = y,
        width = width,
        height = height,
        arcWidth = arcWidth,
        arcHeight = arcHeight
      )
    }
  }

  private fun drawRoundRect(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0] as Int
    val y = args[1] as Int
    val width = args[2] as Int
    val height = args[3] as Int
    val arcWidth = args[4] as Int
    val arcHeight = args[5] as Int

    paintRoundRect(
      paintType = AwtPaintType.DRAW,
      x = x,
      y = y,
      width = width,
      height = height,
      arcWidth = arcWidth,
      arcHeight = arcHeight,
      graphicsState,
      drawEventQueue,
    )
  }

  private fun fillRoundRect(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0] as Int
    val y = args[1] as Int
    val width = args[2] as Int
    val height = args[3] as Int
    val arcWidth = args[4] as Int
    val arcHeight = args[5] as Int

    paintRoundRect(
      paintType = AwtPaintType.FILL,
      x = x,
      y = y,
      width = width,
      height = height,
      arcWidth = arcWidth,
      arcHeight = arcHeight,
      graphicsState,
      drawEventQueue,
    )
  }

  private fun paintOval(
    paintType: AwtPaintType,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    graphicsState: GraphicsState,
    drawEventQueue: DrawEventQueue,
  ) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape(graphicsState, drawEventQueue) {
      paintOval(
        paintType = paintType,
        x = x,
        y = y,
        width = width,
        height = height
      )
    }
  }

  private fun drawOval(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0] as Int
    val y = args[1] as Int
    val width = args[2] as Int
    val height = args[3] as Int

    paintOval(
      paintType = AwtPaintType.DRAW,
      x = x,
      y = y,
      width = width,
      height = height,
      graphicsState,
      drawEventQueue,
    )
  }

  private fun fillOval(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0] as Int
    val y = args[1] as Int
    val width = args[2] as Int
    val height = args[3] as Int

    paintOval(
      paintType = AwtPaintType.FILL,
      x = x,
      y = y,
      width = width,
      height = height,
      graphicsState,
      drawEventQueue,
    )
  }

  private fun paintArc(
    paintType: AwtPaintType,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    startAngle: Int,
    arcAngle: Int,
    graphicsState: GraphicsState,
    drawEventQueue: DrawEventQueue,
  ) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape(graphicsState, drawEventQueue) {
      paintArc(
        paintType = paintType,
        x = x,
        y = y,
        width = width,
        height = height,
        startAngle = startAngle,
        arcAngle = arcAngle
      )
    }
  }

  private fun drawArc(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0] as Int
    val y = args[1] as Int
    val width = args[2] as Int
    val height = args[3] as Int
    val startAngle = args[4] as Int
    val arcAngle = args[5] as Int

    paintArc(
      paintType = AwtPaintType.DRAW,
      x = x,
      y = y,
      width = width,
      height = height,
      startAngle = startAngle,
      arcAngle = arcAngle,
      graphicsState,
      drawEventQueue,
    )
  }

  private fun fillArc(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val x = args[0] as Int
    val y = args[1] as Int
    val width = args[2] as Int
    val height = args[3] as Int
    val startAngle = args[4] as Int
    val arcAngle = args[5] as Int

    paintArc(
      paintType = AwtPaintType.FILL,
      x = x,
      y = y,
      width = width,
      height = height,
      startAngle = startAngle,
      arcAngle = arcAngle,
      graphicsState,
      drawEventQueue,
    )
  }

  private fun drawPolyline(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val xPoints = args[0] as IntArray
    val yPoints = args[1] as IntArray
    val nPoints = args[2] as Int

    if (nPoints <= 0) {
      return
    }

    paintShape(graphicsState, drawEventQueue) { drawPolyline(xPoints.take(nPoints).zip(yPoints.take(nPoints))) }
  }

  private fun paintPolygon(
    paintType: AwtPaintType,
    xPoints: IntArray,
    yPoints: IntArray,
    nPoints: Int,
    g: GraphicsState,
    drawEventQueue: DrawEventQueue,
  ) {
    if (nPoints <= 0) {
      return
    }

    paintShape(g, drawEventQueue) {
      paintPolygon(
        paintType = paintType,
        points = xPoints.take(nPoints).zip(yPoints.take(nPoints))
      )
    }
  }

  private fun drawPolygon(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val xPoints = args[0] as IntArray
    val yPoints = args[1] as IntArray
    val nPoints = args[2] as Int

    paintPolygon(AwtPaintType.DRAW, xPoints, yPoints, nPoints, graphicsState, drawEventQueue)
  }

  private fun fillPolygon(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val xPoints = args[0] as IntArray
    val yPoints = args[1] as IntArray
    val nPoints = args[2] as Int

    paintPolygon(AwtPaintType.FILL, xPoints, yPoints, nPoints, graphicsState, drawEventQueue)
  }

  private fun extractImage(img: Image?, awtImageInfo: AwtImageInfo, methodName: String, g: GraphicsState, drawEventQueue: DrawEventQueue) {
    if (img == null) {
      return
    }

    paintArea(g, drawEventQueue) { drawImage(imageId = ImageCacher.instance.getImageId(img, methodName), awtImageInfo = awtImageInfo) }
  }

  private fun drawImage0(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    extractImage(
      img = args[0] as Image?,
      awtImageInfo = AwtImageInfo.Transformation((args[1] as AffineTransform).toList()),
      methodName = "drawImage(img, xform, obs)",
      g = graphicsState,
      drawEventQueue,
    )
  }

  private fun drawImage1(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    val img = args[0] as BufferedImage? ?: return

    extractImage(
      img = (args[1] as BufferedImageOp).filter(img, null),
      awtImageInfo = AffineTransform(1.0, 0.0, 0.0, 1.0, args[2].asDouble(), args[3].asDouble())
        .toList()
        .let(AwtImageInfo::Transformation),
      methodName = "drawImage(img, xform, obs)",
      g = graphicsState,
      drawEventQueue,
    )
  }

  private fun drawImage2(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    extractImage(
      img = args[0] as Image?,
      awtImageInfo = AwtImageInfo.Point(
        x = args[1] as Int,
        y = args[2] as Int,
        argbBackgroundColor = (args[3] as Color?)?.rgb,
      ),
      methodName = "drawImage(img, x, y, bgcolor, observer)",
      g = graphicsState,
      drawEventQueue,
    )
  }

  private fun drawImage3(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    extractImage(
      img = args[0] as Image?,
      awtImageInfo = AwtImageInfo.Rectangle(
        x = args[1] as Int,
        y = args[2] as Int,
        width = args[3] as Int,
        height = args[4] as Int,
        argbBackgroundColor = (args[5] as Color?)?.rgb,
      ),
      methodName = "drawImage(img, x, y, w, h, bgcolor, observer)",
      g = graphicsState,
      drawEventQueue,
    )
  }

  private fun drawImage4(args: Array<Any?>, graphicsState: GraphicsState, drawEventQueue: DrawEventQueue) {
    extractImage(
      img = args[0] as Image?,
      awtImageInfo = AwtImageInfo.Area(
        dx1 = args[1] as Int,
        dy1 = args[2] as Int,
        dx2 = args[3] as Int,
        dy2 = args[4] as Int,
        sx1 = args[5] as Int,
        sy1 = args[6] as Int,
        sx2 = args[7] as Int,
        sy2 = args[8] as Int,
        argbBackgroundColor = (args[9] as Color?)?.rgb,
      ),
      methodName = "drawImage(img, d..., s..., bgcolor, observer)",
      g = graphicsState,
      drawEventQueue,
    )
  }

  private val commandsMap: Map<String, (Array<Any?>, GraphicsState, DrawEventQueue) -> Unit> = mapOf(
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

  private val logger = Logger<CommandsHandler>()
}
