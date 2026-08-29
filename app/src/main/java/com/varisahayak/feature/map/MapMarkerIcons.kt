package com.varisahayak.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.usecase.Hotspot

/**
 * Draws map markers.
 *
 * Markers are rendered rather than tinted because the product requirements forbid
 * conveying priority by colour alone, and a map pin has nowhere to put a text label. Each
 * priority therefore gets a distinct **shape** as well as a distinct colour, and clusters
 * additionally carry their incident count as a numeral — three independent channels, so
 * the marker stays readable to a colour-blind user and in direct sunlight.
 *
 *   CRITICAL  eight-sided stop-sign outline
 *   HIGH      upward triangle
 *   MEDIUM    square
 *   LOW       circle
 *
 * An SOS cluster additionally gets a heavy outer ring.
 */
object MapMarkerIcons {

    fun forHotspot(
        hotspot: Hotspot,
        fillColor: Color,
        contentColor: Color,
    ): BitmapDescriptor {
        val size = if (hotspot.isSingleIncident) SINGLE_SIZE_PX else CLUSTER_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor.toArgb()
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.08f
        }

        val inset = size * 0.12f
        val path = shapeFor(hotspot.highestPriority, size.toFloat(), inset)

        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)

        // An SOS in the cluster earns an extra ring — it must not read as an ordinary pin.
        if (hotspot.hasSos) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = size * 0.06f
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - ring.strokeWidth, ring)
        }

        if (!hotspot.isSingleIncident) {
            drawCount(canvas, hotspot.incidentCount, size, contentColor.toArgb())
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun shapeFor(priority: IncidentPriority, size: Float, inset: Float): Path {
        val path = Path()
        val left = inset
        val top = inset
        val right = size - inset
        val bottom = size - inset
        val centreX = size / 2f
        val centreY = size / 2f
        val radius = (size / 2f) - inset

        when (priority) {
            IncidentPriority.CRITICAL -> {
                // Octagon — the shape people already read as "stop".
                val step = Math.PI / 4.0
                for (corner in 0 until 8) {
                    val angle = step * corner + step / 2.0
                    val x = centreX + radius * kotlin.math.cos(angle).toFloat()
                    val y = centreY + radius * kotlin.math.sin(angle).toFloat()
                    if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }

            IncidentPriority.HIGH -> {
                path.moveTo(centreX, top)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close()
            }

            IncidentPriority.MEDIUM -> {
                path.addRect(left, top, right, bottom, Path.Direction.CW)
            }

            IncidentPriority.LOW -> {
                path.addCircle(centreX, centreY, radius, Path.Direction.CW)
            }
        }
        return path
    }

    private fun drawCount(canvas: Canvas, count: Int, size: Int, textColor: Int) {
        val label = if (count > 99) "99+" else count.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = size * 0.34f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        // Centre on the glyph bounds, not the font metrics: digits have no descenders and
        // baseline-centring leaves the number visibly high in the shape.
        val bounds = Rect()
        paint.getTextBounds(label, 0, label.length, bounds)
        canvas.drawText(label, size / 2f, size / 2f + bounds.height() / 2f, paint)
    }

    private const val SINGLE_SIZE_PX = 84
    private const val CLUSTER_SIZE_PX = 112
}
