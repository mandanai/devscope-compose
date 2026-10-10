package dev.journeyscope.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.model.Glyph

private const val GRID = 24f
private const val STROKE = 1.8f

@Composable
internal fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, tint) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val u = size.minDimension / GRID
    val stroke = Stroke(width = STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun at(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, at(x1, y1), at(x2, y2), STROKE * u, StrokeCap.Round)
    fun polyline(vararg points: Float, close: Boolean = false) {
        val path = Path()
        for (i in points.indices step 2) {
            if (i == 0) path.moveTo(points[0] * u, points[1] * u) else path.lineTo(points[i] * u, points[i + 1] * u)
        }
        if (close) path.close()
        drawPath(path, color, style = stroke)
    }
    when (glyph) {
        Glyph.Tap -> {
            drawCircle(color, 6f * u, at(12f, 10f), style = stroke)
            drawCircle(color, 1.6f * u, at(12f, 10f))
            line(12f, 16f, 12f, 21f)
        }
        Glyph.Layers -> {
            drawRoundRect(color, at(4f, 4f), Size(16f * u, 6f * u), CornerRadius(2f * u), style = stroke)
            drawRoundRect(color, at(4f, 14f), Size(16f * u, 6f * u), CornerRadius(2f * u), style = stroke)
        }
        Glyph.Flow -> {
            drawCircle(color, 2.6f * u, at(5f, 12f), style = stroke)
            drawCircle(color, 2.6f * u, at(19f, 5f), style = stroke)
            drawCircle(color, 2.6f * u, at(19f, 19f), style = stroke)
            line(7.4f, 11f, 16.6f, 6f)
            line(7.4f, 13f, 16.6f, 18f)
        }
        Glyph.Cylinder -> {
            drawOval(color, at(5f, 3f), Size(14f * u, 5f * u), style = stroke)
            line(5f, 5.5f, 5f, 18.5f)
            line(19f, 5.5f, 19f, 18.5f)
            drawArc(color, 0f, 180f, false, at(5f, 9.5f), Size(14f * u, 5f * u), style = stroke)
            drawArc(color, 0f, 180f, false, at(5f, 16f), Size(14f * u, 5f * u), style = stroke)
        }
        Glyph.Globe -> {
            drawCircle(color, 9f * u, at(12f, 12f), style = stroke)
            drawOval(color, at(8f, 3f), Size(8f * u, 18f * u), style = stroke)
            line(3f, 12f, 21f, 12f)
        }
        Glyph.Check -> {
            drawCircle(color, 9f * u, at(12f, 12f), style = stroke)
            polyline(7.5f, 12.5f, 10.8f, 15.8f, 16.5f, 9f)
        }
        Glyph.Bolt -> polyline(13f, 3f, 6f, 13.5f, 11f, 13.5f, 10f, 21f, 18f, 10f, 13f, 10f, close = true)
        Glyph.Window -> {
            drawRoundRect(color, at(3f, 5f), Size(18f * u, 14f * u), CornerRadius(2.5f * u), style = stroke)
            line(3f, 9.5f, 21f, 9.5f)
        }
        Glyph.Dot -> drawCircle(color, 3f * u, at(12f, 12f))
        Glyph.Bulb -> {
            drawCircle(color, 6f * u, at(12f, 10f), style = stroke)
            line(9.5f, 18f, 14.5f, 18f)
            line(10.5f, 21f, 13.5f, 21f)
        }
        Glyph.Back -> polyline(15f, 5f, 8f, 12f, 15f, 19f)
        Glyph.Close -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }
        Glyph.ChevronDown -> polyline(6f, 9f, 12f, 15f, 18f, 9f)
    }
}
