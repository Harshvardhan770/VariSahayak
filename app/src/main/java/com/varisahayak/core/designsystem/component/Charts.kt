package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Charts.
 *
 * Two, and only two. A line for "how has this moved over the week" and a donut for "what
 * is this made of" — the two questions a coordinator actually asks of aggregate data.
 * Anything else on an operational dashboard is decoration competing with the queue.
 *
 * Both render real series or render nothing. There is no sample data path: a chart with
 * invented numbers on an incident dashboard is not a placeholder, it is misinformation
 * somebody may act on.
 *
 * Drawn with Canvas rather than a charting library. The shapes here are a polyline and an
 * arc; pulling in a dependency for that would cost more than it saves and would bring its
 * own type scale and palette to fight with this one.
 */

/** One line on the trend chart. [points] is oldest-first and must match the label count. */
@Immutable
data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Int>,
)

/** One wedge of a donut. */
@Immutable
data class DonutSlice(
    val label: String,
    val value: Int,
    val color: Color,
)

/**
 * A multi-series trend line.
 *
 * The Y axis always starts at zero. Starting at the data minimum is the standard way to
 * make a flat week look like a crisis, and this chart is read by people deciding where to
 * send responders.
 *
 * @param xLabels one per point, oldest first. Rendered thinned out if they would collide.
 */
@Composable
fun TrendLineChart(
    series: List<ChartSeries>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    emptyMessage: String,
) {
    val colors = VariTheme.colors
    val measurer = rememberTextMeasurer()

    val maxValue = series.flatMap { it.points }.maxOrNull() ?: 0
    if (series.isEmpty() || maxValue == 0) {
        NotConnectedPanel(message = emptyMessage, modifier = modifier)
        return
    }

    // Round the ceiling up to a friendly step so gridline labels are 0/50/100 rather than
    // 0/37/74. A axis nobody can read at a glance is an axis nobody reads.
    val step = niceStep(maxValue)
    val ceiling = ((maxValue + step - 1) / step) * step
    val gridLines = (ceiling / step).coerceIn(1, 5)

    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted)
    val gridColor = colors.cardBorder
    val labelColor = colors.textMuted

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val leftGutter = 34.dp.toPx()
            val bottomGutter = 22.dp.toPx()
            val plotWidth = size.width - leftGutter
            val plotHeight = size.height - bottomGutter

            drawGrid(
                measurer = measurer,
                axisStyle = axisStyle,
                gridColor = gridColor,
                gridLines = gridLines,
                step = step,
                leftGutter = leftGutter,
                plotWidth = plotWidth,
                plotHeight = plotHeight,
            )

            val pointCount = series.first().points.size
            if (pointCount < 2) return@Canvas
            val dx = plotWidth / (pointCount - 1)

            series.forEach { line ->
                val offsets = line.points.mapIndexed { index, value ->
                    Offset(
                        x = leftGutter + dx * index,
                        y = plotHeight - (value.toFloat() / ceiling) * plotHeight,
                    )
                }

                for (i in 0 until offsets.size - 1) {
                    drawLine(
                        color = line.color,
                        start = offsets[i],
                        end = offsets[i + 1],
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Filled dots on every vertex. With four overlapping series the vertices
                // are the only thing that lets you follow one line across a crossing.
                offsets.forEach { point ->
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = point)
                    drawCircle(color = line.color, radius = 3.dp.toPx(), center = point)
                }
            }

            // X labels, thinned so they never overlap: at seven days on a 360dp phone only
            // every other one fits.
            val everyNth = if (dx < 44.dp.toPx()) 2 else 1
            xLabels.forEachIndexed { index, label ->
                if (index % everyNth != 0) return@forEachIndexed
                val measured = measurer.measure(label, axisStyle)
                drawText(
                    textMeasurer = measurer,
                    text = label,
                    style = axisStyle.copy(color = labelColor),
                    topLeft = Offset(
                        x = (leftGutter + dx * index - measured.size.width / 2f)
                            .coerceIn(0f, size.width - measured.size.width),
                        y = plotHeight + 6.dp.toPx(),
                    ),
                )
            }
        }

        ChartLegend(
            entries = series.map { it.label to it.color },
            modifier = Modifier.padding(top = Dimens.SpaceSm),
        )
    }
}

private fun DrawScope.drawGrid(
    measurer: TextMeasurer,
    axisStyle: TextStyle,
    gridColor: Color,
    gridLines: Int,
    step: Int,
    leftGutter: Float,
    plotWidth: Float,
    plotHeight: Float,
) {
    for (i in 0..gridLines) {
        val value = step * i
        val y = plotHeight - (i.toFloat() / gridLines) * plotHeight

        drawLine(
            color = gridColor,
            start = Offset(leftGutter, y),
            end = Offset(leftGutter + plotWidth, y),
            strokeWidth = 1.dp.toPx(),
            // Dashed above the baseline, solid on it: the zero line is a fact, the rest are
            // reading aids and should not compete with the data.
            pathEffect = if (i == 0) {
                null
            } else {
                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            },
        )

        val measured = measurer.measure(value.toString(), axisStyle)
        drawText(
            textMeasurer = measurer,
            text = value.toString(),
            style = axisStyle,
            topLeft = Offset(
                x = leftGutter - measured.size.width - 6.dp.toPx(),
                y = y - measured.size.height / 2f,
            ),
        )
    }
}

/**
 * Composition breakdown.
 *
 * A donut rather than a pie because the hole carries the total, which is the number people
 * actually want and which a pie has nowhere to put.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centreValue: String,
    centreLabel: String,
    modifier: Modifier = Modifier,
    emptyMessage: String,
) {
    val colors = VariTheme.colors
    val total = slices.sumOf { it.value }

    if (total == 0) {
        NotConnectedPanel(message = emptyMessage, modifier = modifier)
        return
    }

    Box(modifier = modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2f
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )

            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value.toFloat() / total)
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    // A hairline gap between wedges. Two adjacent slices of similar hue
                    // otherwise read as one.
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centreValue,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            Text(
                text = centreLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }
}

/**
 * The donut's legend, as a value table.
 *
 * Rows rather than chips: each line carries a name, a count and a share, and those only
 * line up into something scannable in a column.
 */
@Composable
fun DonutLegend(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val total = slices.sumOf { it.value }.coerceAtLeast(1)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        slices.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(slice.color),
                )
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${slice.value}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "${(slice.value * 100f / total).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ChartLegend(
    entries: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    // Wrapped rather than scrolled: a legend the reader has to drag is one they will not
    // read, and unlike a filter row it has no depth to hide.
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        entries.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** 1, 2, 5, 10, 20, 50 … — the steps people read without doing arithmetic. */
private fun niceStep(maxValue: Int): Int {
    val target = max(1, maxValue) / 4.0
    val candidates = listOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000)
    return candidates.firstOrNull { it >= target } ?: 1000
}
