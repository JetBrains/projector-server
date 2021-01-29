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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.awt.image

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.data.AwtImageInfo
import org.jetbrains.projector.awt.data.AwtPaintType
import org.jetbrains.projector.awt.font.PFontManager
import org.jetbrains.projector.awt.service.Defaults
import org.jetbrains.projector.awt.service.DrawEventQueue
import org.jetbrains.projector.awt.service.ImageCacher
import org.jetbrains.projector.util.logging.Logger
import sun.font.FontDesignMetrics
import sun.java2d.NullSurfaceData
import sun.java2d.SunGraphics2D
import java.awt.*
import java.awt.RenderingHints.Key
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.awt.image.RenderedImage
import java.awt.image.renderable.RenderableImage
import java.text.AttributedCharacterIterator
import javax.swing.UIManager

class PGraphics2D private constructor(
  val drawEventQueue: DrawEventQueue,
  private var backingComposite: Composite,
  private val hints: RenderingHints,
  private var identitySpaceClip: Shape?,
  private val transform: AffineTransform,
  private var backgroundColor: Color,
  private var paint: Paint,
  private var foregroundColor: Color,
  private var stroke: Stroke,
  private var font: Font,
) : Graphics2D() {

  private var backingFontRenderContext = FontRenderContext(
    transform.withoutTranslation,
    extractTextAntiAliasingHint(hints),
    extractFractionalMetricsHint(hints)
  )
    get() {
      val currentTransform = transform.withoutTranslation
      val currentAa = extractTextAntiAliasingHint(hints)
      val currentFm = extractFractionalMetricsHint(hints)
      if (field.transform != currentTransform || field.antiAliasingHint != currentAa || field.fractionalMetricsHint != currentFm) {
        field = FontRenderContext(currentTransform, currentAa, currentFm)
      }

      return field
    }

  private inline fun paintPlain(crossinline command: DrawEventQueue.CommandBuilder.() -> Unit) {
    drawEventQueue.buildCommand().command()
  }

  private inline fun paintShape(crossinline command: DrawEventQueue.CommandBuilder.() -> Unit) {
    drawEventQueue
      .buildCommand()
      .setClip(identitySpaceClip = identitySpaceClip)
      .setTransform(transform.toList())
      .setStroke(stroke)
      .setPaint(paint)
      .setComposite(backingComposite)
      .command()
  }

  private inline fun paintArea(crossinline command: DrawEventQueue.CommandBuilder.() -> Unit) {
    drawEventQueue
      .buildCommand()
      .setClip(identitySpaceClip = identitySpaceClip)
      .setTransform(transform.toList())
      .setComposite(backingComposite)
      .command()
  }

  private fun paintString(str: String, x: Double, y: Double) {
    if (str.isBlank()) {
      return
    }

    val metrics = FontDesignMetrics.getMetrics(font, backingFontRenderContext)
    val desiredWidth = metrics.stringWidth(str)

    drawEventQueue
      .buildCommand()
      .setClip(identitySpaceClip = identitySpaceClip)
      .setTransform(transform.toList())
      .setFont(font)
      .setPaint(paint)
      .setComposite(backingComposite)
      .drawString(str, x = x, y = y, desiredWidth = desiredWidth.toDouble())
  }

  constructor(target: PVolatileImage.Descriptor) : this(
    drawEventQueue = DrawEventQueue.createOffScreen(target),
    transform = AffineTransform(),  // from Graphics2D "for image buffers, default transform is identity" java doc
    backgroundColor = null,
    paint = null,
    foregroundColor = null,
    font = null
  )

  constructor(component: Component, target: PWindow.Descriptor) : this(
    drawEventQueue = DrawEventQueue.createOnScreen(target),
    transform = component.graphicsConfiguration.defaultTransform,  // from Graphics2D "Default Rendering Attributes" java doc
    backgroundColor = component.background,  // from Graphics2D "Default Rendering Attributes" java doc
    paint = component.foreground,  // from Graphics2D "Default Rendering Attributes" java doc
    foregroundColor = component.foreground,  // from Graphics2D "Default Rendering Attributes" java doc
    font = component.font
  )

  private constructor(
    drawEventQueue: DrawEventQueue,
    transform: AffineTransform,
    backgroundColor: Color?,
    paint: Paint?,
    foregroundColor: Color?,
    font: Font?,
  ) : this(
    drawEventQueue = drawEventQueue,
    backingComposite = AlphaComposite.SrcOver,  // from Graphics2D "Default Rendering Attributes" java doc
    hints = makeHints(null),
    identitySpaceClip = null,  // from Graphics2D "Default Rendering Attributes" java doc
    transform = transform,
    backgroundColor = backgroundColor ?: Defaults.BACKGROUND_COLOR_ARGB,
    paint = paint ?: Defaults.FOREGROUND_COLOR_ARGB,
    foregroundColor = foregroundColor ?: Defaults.FOREGROUND_COLOR_ARGB,
    stroke = Defaults.STROKE,
    font = font ?: PFontManager.allInstalledFonts.first()
  )

  override fun create(): Graphics {
    return PGraphics2D(
      drawEventQueue = drawEventQueue,
      backingComposite = backingComposite,
      hints = makeHints(null).apply { add(hints) },
      identitySpaceClip = identitySpaceClip?.copy(),
      transform = AffineTransform(transform),
      backgroundColor = backgroundColor,
      paint = paint,
      foregroundColor = foregroundColor,
      stroke = stroke,
      font = font
    )
  }

  override fun draw(s: Shape) {
    paintShape(AwtPaintType.DRAW, s)
  }

  override fun drawRenderedImage(img: RenderedImage, xform: AffineTransform) {
    paintPlain { drawRenderedImage() }
  }

  override fun drawRenderableImage(img: RenderableImage, xform: AffineTransform) {
    paintPlain { drawRenderableImage() }
  }

  override fun drawString(str: String, x: Int, y: Int) {
    paintString(str, x = x.toDouble(), y = y.toDouble())
  }

  override fun drawString(str: String, x: Float, y: Float) {
    paintString(str, x = x.toDouble(), y = y.toDouble())
  }

  override fun drawString(iterator: AttributedCharacterIterator, x: Int, y: Int) {
    drawString(iterator, x = x.toFloat(), y = y.toFloat())
  }

  override fun drawString(iterator: AttributedCharacterIterator, x: Float, y: Float) {
    if (iterator.beginIndex != iterator.endIndex) {
      val tl = TextLayout(iterator, this.backingFontRenderContext)
      tl.draw(this, x, y)
    }
  }

  override fun drawGlyphVector(g: GlyphVector, x: Float, y: Float) {
    val shape = g.getOutline(x, y)
    fill(shape)
  }

  private fun paintShape(paintType: AwtPaintType, shape: Shape) {
    when (shape) {
      is Rectangle2D -> addPaintRectCommand(paintType, shape.x, shape.y, shape.width, shape.height)

      else -> paintShape { paintPath(paintType, shape) }
    }
  }

  override fun fill(s: Shape) {
    paintShape(AwtPaintType.FILL, s)
  }

  override fun hit(rect: Rectangle, s: Shape, onStroke: Boolean): Boolean {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "hit");
    //addCommand(obj);
    return false
  }

  override fun getDeviceConfiguration(): GraphicsConfiguration {
    return PGraphicsDevice.defaultConfiguration
  }

  override fun setComposite(comp: Composite) {
    backingComposite = comp
  }

  override fun setPaint(paint: Paint?) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "setPaint");
    //addCommand(obj);

    //org.apache.batik.ext.awt.LinearGradientPaint lgp =
    //  paint instanceof org.apache.batik.ext.awt.LinearGradientPaint ? (org.apache.batik.ext.awt.LinearGradientPaint)paint : null;
    //if (lgp != null) {
    //    JsonObject obj = new JsonObject();
    //    obj.addProperty("method", "setPaintLinearGradient");
    //
    //    obj.add("start", pointToJson(lgp.getStartPoint()));
    //    obj.add("end", pointToJson(lgp.getEndPoint()));
    //
    //    obj.addProperty("transparency", lgp.getTransparency());
    //    obj.add("colors", colorsToJsonArray(lgp.getColors()));
    //    obj.add("fractions", floatsToJsonArray(lgp.getFractions()));
    //
    //    obj.add("gradientTransform", transformToJson(lgp.getTransform()));
    //
    //    addCommand(obj);
    //    return;
    //}
    if (paint == null) {
      return  // from java doc: "null doesn't affect the current Paint attribute"
    }

    if (paint is Color) {
      color = paint
    }
    else {
      this.paint = paint
    }
  }

  override fun setStroke(s: Stroke) {
    stroke = s
  }

  override fun setRenderingHint(hintKey: Key?, hintValue: Any?) {
    if (hintKey == null || hintValue == null) {
      return
    }

    hints[hintKey] = hintValue
  }

  override fun getRenderingHint(hintKey: Key): Any? {
    return hints[hintKey]
  }

  override fun setRenderingHints(hints: Map<*, *>) {
    this.hints.apply {
      clear()
      putAll(hints)
    }
  }

  override fun addRenderingHints(hints: Map<*, *>) {
    this.hints.putAll(hints)
  }

  override fun getRenderingHints(): RenderingHints {
    return hints.clone() as RenderingHints
  }

  override fun translate(x: Int, y: Int) {
    translate(x.toDouble(), y.toDouble())
  }

  override fun translate(x: Double, y: Double) {
    transform.translate(x, y)
  }

  override fun rotate(radians: Double) {
    transform.rotate(radians)
  }

  override fun rotate(theta: Double, x: Double, y: Double) {
    transform.rotate(theta, x, y)
  }

  override fun scale(sx: Double, sy: Double) {
    transform.scale(sx, sy)
  }

  override fun shear(shx: Double, shy: Double) {
    transform.shear(shx, shy)
  }

  override fun transform(Tx: AffineTransform) {
    transform.concatenate(Tx)
  }

  override fun setTransform(tx: AffineTransform) {
    transform.setTransform(tx)
  }

  override fun getTransform(): AffineTransform {
    return AffineTransform(transform)
  }

  override fun getPaint(): Paint {
    return paint
  }

  override fun getComposite(): Composite {
    return backingComposite
  }

  override fun setBackground(color: Color?) {
    color ?: return  // todo

    backgroundColor = color
  }

  override fun getBackground(): Color {
    return backgroundColor
  }

  override fun getStroke(): Stroke {
    return stroke
  }

  override fun getFontRenderContext(): FontRenderContext {
    return backingFontRenderContext
  }

  override fun getColor(): Color {
    return foregroundColor
  }

  override fun setColor(color: Color?) {
    if (color == null) {
      return
    }

    paint = color
    foregroundColor = color
  }

  override fun setPaintMode() {
    composite = AlphaComposite.SrcOver  // https://docs.oracle.com/javase/7/docs/api/java/awt/Graphics2D.html
  }

  override fun setXORMode(color: Color) {
    // todo: https://docs.oracle.com/javase/7/docs/api/java/awt/Graphics2D.html
  }

  override fun getFont(): Font {
    return fontMetrics.font
  }

  override fun setFont(font: Font?) {
    if (font == null) {  // from java doc: "null argument is silently ignored"
      return
    }

    this.font = font
  }

  override fun getFontMetrics(): FontMetrics {
    return FontDesignMetrics.getMetrics(font, backingFontRenderContext)
  }

  override fun getFontMetrics(newFont: Font): FontMetrics {
    if (newFont == this.font) {
      return fontMetrics
    }

    return FontDesignMetrics.getMetrics(newFont, backingFontRenderContext)
  }

  override fun getClipBounds(): Rectangle? {
    return identitySpaceClip?.untransformShape()?.bounds
  }

  override fun clipRect(x: Int, y: Int, w: Int, h: Int) {
    clip(Rectangle(x, y, w, h))
  }

  override fun setClip(x: Int, y: Int, w: Int, h: Int) {
    clip = Rectangle(x, y, w, h)
  }

  private fun Shape.untransformShape(): Shape {
    return this.transformShape(transform.createInverse())
  }

  override fun getClip(): Shape? {
    try {
      return identitySpaceClip?.untransformShape()
    }
    catch (e: NoninvertibleTransformException) {
      logger.error(e) { "Can't return clip because it's noninvertible" }
      return null
    }
  }

  override fun clip(s: Shape?) {
    if (s == null) {  // java doc: if s is null, the method removes the current clip
      identitySpaceClip = null

      return
    }

    var identitySpaceShape = s.transformShape(transform)

    if (identitySpaceShape == identitySpaceClip) {
      return
    }

    if (identitySpaceShape.bounds.isEmpty) {
      // todo
    }

    identitySpaceClip?.let {
      val intersection = intersectShapes(it, identitySpaceShape, keep1 = true, keep2 = true)

      if (intersection.bounds.isEmpty) {
        // todo
      }

      identitySpaceShape = intersection
    }

    identitySpaceClip = identitySpaceShape
  }

  override fun setClip(s: Shape?) {
    val identitySpaceShape = s?.transformShape(transform)

    if (identitySpaceShape == identitySpaceClip) {
      return
    }

    identitySpaceClip = identitySpaceShape
  }

  override fun copyArea(x: Int, y: Int, width: Int, height: Int, dx: Int, dy: Int) {
    paintArea {
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

  override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
    paintShape {
      drawLine(
        x1 = x1,
        y1 = y1,
        x2 = x2,
        y2 = y2
      )
    }
  }

  private fun addPaintRectCommand(paintType: AwtPaintType, x: Double, y: Double, width: Double, height: Double) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape {
      paintRect(
        paintType = paintType,
        x = x,
        y = y,
        width = width,
        height = height
      )
    }
  }

  override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
    addPaintRectCommand(AwtPaintType.FILL, x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())
  }

  override fun clearRect(x: Int, y: Int, width: Int, height: Int) {
    val c = composite
    val p = getPaint()
    composite = AlphaComposite.Src
    color = background
    fillRect(x, y, width, height)
    setPaint(p)
    composite = c
  }

  private fun paintRoundRect(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape {
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

  override fun drawRoundRect(x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    paintRoundRect(
      paintType = AwtPaintType.DRAW,
      x = x,
      y = y,
      width = width,
      height = height,
      arcWidth = arcWidth,
      arcHeight = arcHeight
    )
  }

  override fun fillRoundRect(x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    paintRoundRect(
      paintType = AwtPaintType.FILL,
      x = x,
      y = y,
      width = width,
      height = height,
      arcWidth = arcWidth,
      arcHeight = arcHeight
    )
  }

  private fun paintOval(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape {
      paintOval(
        paintType = paintType,
        x = x,
        y = y,
        width = width,
        height = height
      )
    }
  }

  override fun drawOval(x: Int, y: Int, width: Int, height: Int) {
    paintOval(
      paintType = AwtPaintType.DRAW,
      x = x,
      y = y,
      width = width,
      height = height
    )
  }

  override fun fillOval(x: Int, y: Int, width: Int, height: Int) {
    paintOval(
      paintType = AwtPaintType.FILL,
      x = x,
      y = y,
      width = width,
      height = height
    )
  }

  private fun paintArc(paintType: AwtPaintType, x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    if (width <= 0 || height <= 0) {
      return
    }

    paintShape {
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

  override fun drawArc(x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    paintArc(
      paintType = AwtPaintType.DRAW,
      x = x,
      y = y,
      width = width,
      height = height,
      startAngle = startAngle,
      arcAngle = arcAngle
    )
  }

  override fun fillArc(x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    paintArc(
      paintType = AwtPaintType.FILL,
      x = x,
      y = y,
      width = width,
      height = height,
      startAngle = startAngle,
      arcAngle = arcAngle
    )
  }

  override fun drawPolyline(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    if (nPoints <= 0) {
      return
    }

    paintShape { drawPolyline(xPoints.take(nPoints).zip(yPoints.take(nPoints))) }
  }

  private fun paintPolygon(paintType: AwtPaintType, xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    if (nPoints <= 0) {
      return
    }

    paintShape {
      paintPolygon(
        paintType = paintType,
        points = xPoints.take(nPoints).zip(yPoints.take(nPoints))
      )
    }
  }

  override fun drawPolygon(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    paintPolygon(AwtPaintType.DRAW, xPoints, yPoints, nPoints)
  }

  override fun fillPolygon(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
    paintPolygon(AwtPaintType.FILL, xPoints, yPoints, nPoints)
  }

  override fun drawImage(img: Image?, xform: AffineTransform, obs: ImageObserver?): Boolean {
    val info = AwtImageInfo.Transformation(xform.toList())

    return extractImage(img, info, "drawImage(img, xform, obs)")
  }

  override fun drawImage(img: BufferedImage?, op: BufferedImageOp, x: Int, y: Int) {
    if (img == null) {  // from java doc
      return
    }

    drawImage(op.filter(img, null), AffineTransform(1.0, 0.0, 0.0, 1.0, x.toDouble(), y.toDouble()), null)
  }

  override fun drawImage(img: Image?, x: Int, y: Int, observer: ImageObserver?): Boolean {
    return drawImage(img = img, x = x, y = y, bgcolor = null, observer = observer)
  }

  override fun drawImage(img: Image?, x: Int, y: Int, bgcolor: Color?, observer: ImageObserver?): Boolean {
    val info = AwtImageInfo.Point(x, y, bgcolor?.rgb)
    return extractImage(img, info, "drawImage(img, x, y, bgcolor, observer)")
  }

  override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver?): Boolean {
    return drawImage(img = img, x = x, y = y, width = width, height = height, bgcolor = null, observer = observer)
  }

  override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, bgcolor: Color?, observer: ImageObserver?): Boolean {
    val info = AwtImageInfo.Rectangle(x, y, width, height, bgcolor?.rgb)

    return extractImage(img, info, "drawImage(img, x, y, w, h, bgcolor, observer)")
  }

  override fun drawImage(
    img: Image?,
    dx1: Int, dy1: Int, dx2: Int, dy2: Int,
    sx1: Int, sy1: Int, sx2: Int, sy2: Int,
    observer: ImageObserver?,
  ): Boolean {
    return drawImage(
      img = img,
      dx1 = dx1, dy1 = dy1, dx2 = dx2, dy2 = dy2,
      sx1 = sx1, sy1 = sy1, sx2 = sx2, sy2 = sy2,
      bgcolor = null,
      observer = observer
    )
  }

  override fun drawImage(
    img: Image?,
    dx1: Int, dy1: Int, dx2: Int, dy2: Int,
    sx1: Int, sy1: Int, sx2: Int, sy2: Int,
    bgcolor: Color?,
    observer: ImageObserver?,
  ): Boolean {
    val info = AwtImageInfo.Area(
      dx1 = dx1, dy1 = dy1, dx2 = dx2, dy2 = dy2,
      sx1 = sx1, sy1 = sy1, sx2 = sx2, sy2 = sy2,
      argbBackgroundColor = bgcolor?.rgb
    )

    return extractImage(img, info, "drawImage(img, d..., s..., bgcolor, observer)")
  }

  private fun extractImage(img: Image?, awtImageInfo: AwtImageInfo, methodName: String): Boolean {
    if (img == null) {
      return true  // java doc for all image methods: methods just return true if the img is null
    }

    paintArea { drawImage(imageId = ImageCacher.instance.getImageId(img, methodName), awtImageInfo = awtImageInfo) }

    return true
  }

  override fun dispose() {
  }

  companion object {

    private val logger = Logger<PGraphics2D>()

    private fun extractTextAntiAliasingHint(hints: RenderingHints) = hints[RenderingHints.KEY_TEXT_ANTIALIASING]
                                                                     ?: UIManager.getDefaults()[RenderingHints.KEY_TEXT_ANTIALIASING]
                                                                     ?: RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT

    private fun extractFractionalMetricsHint(hints: RenderingHints) = hints[RenderingHints.KEY_FRACTIONALMETRICS]
                                                                      ?: UIManager.getDefaults()[RenderingHints.KEY_FRACTIONALMETRICS]
                                                                      ?: RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT

    private val AffineTransform.withoutTranslation
      get() = AffineTransform(this).apply {
        setTransform(scaleX, shearY, shearX, scaleY, 0.0, 0.0)
      }

    private val defaultRenderingHints = SunGraphics2D(
      NullSurfaceData.theInstance,
      Color.BLACK,
      Color.WHITE,
      PFontManager.allInstalledFonts.first()
    ).renderingHints

    val defaultAa: Any? = extractTextAntiAliasingHint(defaultRenderingHints)

    private fun makeHints(hints: MutableMap<Key, *>?): RenderingHints {
      return RenderingHints(null).apply {
        add(defaultRenderingHints)

        hints?.let { putAll(it) }
      }
    }
  }
}
