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

package org.jetbrains.projector.server.util

import org.jetbrains.projector.awt.PWindow
import org.jetbrains.projector.awt.data.AwtImageInfo
import org.jetbrains.projector.awt.data.AwtPaintType
import org.jetbrains.projector.awt.data.Direction
import org.jetbrains.projector.common.protocol.data.*
import org.jetbrains.projector.common.protocol.data.Point
import org.jetbrains.projector.common.protocol.toClient.*
import org.jetbrains.projector.common.protocol.toServer.ClientKeyEvent
import org.jetbrains.projector.common.protocol.toServer.ClientMouseEvent
import org.jetbrains.projector.common.protocol.toServer.ResizeDirection
import org.jetbrains.projector.server.log.Logger
import java.awt.*
import java.awt.Cursor.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.geom.PathIterator
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.Popup
import kotlin.math.ceil
import kotlin.math.floor

fun Point2D.toPoint() = Point(x, y)

fun Dimension.toCommonIntSize() = CommonIntSize(width, height)

fun Rectangle.toCommonRectangle() = CommonRectangle(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())

fun Shape.toCommonPath(): CommonPath {
  val segments = mutableListOf<PathSegment>()

  val pi = this.getPathIterator(null)

  while (!pi.isDone) {
    val coordinates = DoubleArray(6)
    val pathSegmentType = pi.currentSegment(coordinates)

    val points = coordinates
      .asList()
      .chunked(2)
      .map { (x, y) -> Point(x, y) }

    val segment = when (pathSegmentType) {
      PathIterator.SEG_MOVETO -> PathSegment.MoveTo(points[0])
      PathIterator.SEG_LINETO -> PathSegment.LineTo(points[0])
      PathIterator.SEG_QUADTO -> PathSegment.QuadTo(points[0], points[1])
      PathIterator.SEG_CUBICTO -> PathSegment.CubicTo(points[0], points[1], points[2])
      PathIterator.SEG_CLOSE -> PathSegment.Close

      else -> throw IllegalArgumentException("Unsupported path segment type: $pathSegmentType")
    }

    segments.add(segment)
    pi.next()
  }

  val windingType = when (val windingRule = pi.windingRule) {
    PathIterator.WIND_EVEN_ODD -> CommonPath.WindingType.EVEN_ODD
    PathIterator.WIND_NON_ZERO -> CommonPath.WindingType.NON_ZERO

    else -> throw IllegalArgumentException("Unsupported winding rule: $windingRule")
  }

  return CommonPath(segments, windingType)
}

/* Converts an ARGB number to a color. */
fun Number.toColor(): Color = Color(this.toInt(), true)

fun StrokeData.toStroke(): Stroke {
  when (this) {
    is StrokeData.Basic -> {
      val cap = when (endCap) {
        StrokeData.Basic.CapType.BUTT -> BasicStroke.CAP_BUTT
        StrokeData.Basic.CapType.SQUARE -> BasicStroke.CAP_SQUARE
        StrokeData.Basic.CapType.ROUND -> BasicStroke.CAP_ROUND
      }

      val join = when (lineJoin) {
        StrokeData.Basic.JoinType.MITER -> BasicStroke.JOIN_MITER
        StrokeData.Basic.JoinType.BEVEL -> BasicStroke.JOIN_BEVEL
        StrokeData.Basic.JoinType.ROUND -> BasicStroke.JOIN_ROUND
      }

      return BasicStroke(
        lineWidth,
        cap,
        join,
        miterLimit,
        dashArray?.toFloatArray(),
        dashPhase
      )
    }
  }
}

fun BasicStroke.toBasicStrokeData(): StrokeData.Basic {
  val cap = when (val cap = endCap) {
    BasicStroke.CAP_BUTT -> StrokeData.Basic.CapType.BUTT
    BasicStroke.CAP_SQUARE -> StrokeData.Basic.CapType.SQUARE
    BasicStroke.CAP_ROUND -> StrokeData.Basic.CapType.ROUND

    else -> throw IllegalArgumentException("Bad s.endCap: $cap")
  }

  val join = when (val join = lineJoin) {
    BasicStroke.JOIN_MITER -> StrokeData.Basic.JoinType.MITER
    BasicStroke.JOIN_BEVEL -> StrokeData.Basic.JoinType.BEVEL
    BasicStroke.JOIN_ROUND -> StrokeData.Basic.JoinType.ROUND

    else -> throw IllegalArgumentException("Bad s.lineJoin: $join")
  }

  return StrokeData.Basic(
    lineWidth = lineWidth,
    lineJoin = join,
    endCap = cap,
    miterLimit = miterLimit,
    dashPhase = dashPhase,
    dashArray = dashArray?.toList()
  )
}

fun Int.toCursorType() = when (this) {
  DEFAULT_CURSOR -> CursorType.DEFAULT
  CROSSHAIR_CURSOR -> CursorType.CROSSHAIR
  TEXT_CURSOR -> CursorType.TEXT
  WAIT_CURSOR -> CursorType.WAIT
  SW_RESIZE_CURSOR -> CursorType.SW_RESIZE
  SE_RESIZE_CURSOR -> CursorType.SE_RESIZE
  NW_RESIZE_CURSOR -> CursorType.NW_RESIZE
  NE_RESIZE_CURSOR -> CursorType.NE_RESIZE
  N_RESIZE_CURSOR -> CursorType.N_RESIZE
  S_RESIZE_CURSOR -> CursorType.S_RESIZE
  W_RESIZE_CURSOR -> CursorType.W_RESIZE
  E_RESIZE_CURSOR -> CursorType.E_RESIZE
  HAND_CURSOR -> CursorType.HAND
  MOVE_CURSOR -> CursorType.MOVE

  else -> {
    logger.error { "Int.toCursorType(): Bad cursor id: $this. Returning default." }

    CursorType.DEFAULT
  }
}

fun Paint.toPaintValue(): PaintValue = when (this) {
  is Color -> PaintValue.Color(rgb)

  is GradientPaint -> PaintValue.Gradient(
    p1 = point1.toPoint(),
    p2 = point2.toPoint(),
    argb1 = color1.rgb,
    argb2 = color2.rgb
  )

  is MultipleGradientPaint -> PaintValue.Unknown("MultipleGradientPaint, maybe split to Linear and Radial")

  is TexturePaint -> PaintValue.Unknown("TexturePaint")

  else -> PaintValue.Unknown(this::class.qualifiedName.toString())
}

fun createSetClipEvent(identitySpaceClip: Shape?): ServerWindowStateEvent = ServerSetClipEvent(
  with(identitySpaceClip) {
    when (this) {
      null -> null

      is Rectangle2D -> CommonRectangle(x, y, width, height)

      else -> this.toCommonPath()
    }
  }
)

fun Stroke.toSetStrokeEvent(): ServerWindowStateEvent = when (this) {
  is BasicStroke -> ServerSetStrokeEvent(this.toBasicStrokeData())

  else -> ServerSetUnknownStrokeEvent(this::class.qualifiedName.toString())
}

private fun AlphaComposite.toCommonAlphaComposite(): CommonAlphaComposite {
  val acRule = when (rule) {
    AlphaComposite.SRC_OVER -> AlphaCompositeRule.SRC_OVER
    AlphaComposite.DST_OVER -> AlphaCompositeRule.DST_OVER
    AlphaComposite.SRC_IN -> AlphaCompositeRule.SRC_IN
    AlphaComposite.CLEAR -> AlphaCompositeRule.CLEAR
    AlphaComposite.SRC -> AlphaCompositeRule.SRC
    AlphaComposite.DST -> AlphaCompositeRule.DST
    AlphaComposite.DST_IN -> AlphaCompositeRule.DST_IN
    AlphaComposite.SRC_OUT -> AlphaCompositeRule.SRC_OUT
    AlphaComposite.DST_OUT -> AlphaCompositeRule.DST_OUT
    AlphaComposite.SRC_ATOP -> AlphaCompositeRule.SRC_ATOP
    AlphaComposite.DST_ATOP -> AlphaCompositeRule.DST_ATOP
    AlphaComposite.XOR -> AlphaCompositeRule.XOR

    else -> {
      logger.error { "AlphaComposite.toCommonAlphaComposite: Bad alpha composite rule: $rule. Returning SRC_OVER." }

      AlphaCompositeRule.SRC_OVER
    }
  }

  return CommonAlphaComposite(acRule, alpha)
}

fun Composite.toCommonComposite(): CommonComposite = when (this) {
  is AlphaComposite -> this.toCommonAlphaComposite()

  else -> UnknownComposite("Unknown composite class: ${this::class.java.canonicalName}")
}

fun ClientMouseEvent.MouseEventType.toAwtMouseEventId() = when (this) {
  ClientMouseEvent.MouseEventType.MOVE -> MouseEvent.MOUSE_MOVED
  ClientMouseEvent.MouseEventType.DOWN -> MouseEvent.MOUSE_PRESSED
  ClientMouseEvent.MouseEventType.UP -> MouseEvent.MOUSE_RELEASED
  ClientMouseEvent.MouseEventType.CLICK -> MouseEvent.MOUSE_CLICKED
  ClientMouseEvent.MouseEventType.OUT -> MouseEvent.MOUSE_EXITED
  ClientMouseEvent.MouseEventType.DRAG -> MouseEvent.MOUSE_DRAGGED
}

fun ClientKeyEvent.KeyEventType.toAwtKeyEventId() = when (this) {
  ClientKeyEvent.KeyEventType.DOWN -> KeyEvent.KEY_PRESSED
  ClientKeyEvent.KeyEventType.UP -> KeyEvent.KEY_RELEASED
}

fun ClientKeyEvent.KeyLocation.toJavaLocation() = when (this) {
  ClientKeyEvent.KeyLocation.STANDARD -> KeyEvent.KEY_LOCATION_STANDARD
  ClientKeyEvent.KeyLocation.LEFT -> KeyEvent.KEY_LOCATION_LEFT
  ClientKeyEvent.KeyLocation.RIGHT -> KeyEvent.KEY_LOCATION_RIGHT
  ClientKeyEvent.KeyLocation.NUMPAD -> KeyEvent.KEY_LOCATION_NUMPAD
}

fun roundToInfinity(x: Double): Double = when {
  x.isNaN() || x.isInfinite() -> x
  x > 0 -> ceil(x)
  else -> floor(x)
}

fun AwtPaintType.toPaintType() = when (this) {
  AwtPaintType.DRAW -> PaintType.DRAW
  AwtPaintType.FILL -> PaintType.FILL
}

fun AwtImageInfo.toImageEventInfo() = when (this) {
  is AwtImageInfo.Point -> ImageEventInfo.Xy(x = x, y = y)
  is AwtImageInfo.Rectangle -> ImageEventInfo.XyWh(x = x, y = y, width = width, height = height, argbBackgroundColor = argbBackgroundColor)
  is AwtImageInfo.Area -> ImageEventInfo.Ds(
    dx1 = dx1, dy1 = dy1, dx2 = dx2, dy2 = dy2,
    sx1 = sx1, sy1 = sy1, sx2 = sx2, sy2 = sy2,
    argbBackgroundColor = argbBackgroundColor
  )
  is AwtImageInfo.Transformation -> ImageEventInfo.Transformed(tx)
}

val PWindow.windowType: WindowType
  get() = when {
    "IdeFrameImpl" in target::class.java.simpleName -> WindowType.IDEA_WINDOW
    target is Popup -> WindowType.POPUP
    else -> WindowType.WINDOW
  }

fun ResizeDirection.toDirection() = when (this) {
  ResizeDirection.NW -> Direction.NW
  ResizeDirection.SW -> Direction.SW
  ResizeDirection.NE -> Direction.NE
  ResizeDirection.SE -> Direction.SE
  ResizeDirection.N -> Direction.N
  ResizeDirection.W -> Direction.W
  ResizeDirection.S -> Direction.S
  ResizeDirection.E -> Direction.E
}

private val logger = Logger("ConvertKt")
