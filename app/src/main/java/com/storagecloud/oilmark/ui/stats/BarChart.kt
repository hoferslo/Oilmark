package com.storagecloud.oilmark.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.Bucket

/*
 * The two chart types of the app.
 *  - BarChart: amounts that add up per period (distance driven, money spent, fuel bought).
 *  - LineChart: a level that moves over time (consumption, price per litre).
 */

/**
 * Chart colours, one per series, always in this order (a series keeps its colour no matter
 * which others are shown). Chosen to stay tellable-apart for colour-blind readers; the dark
 * set is the same hues tuned for a dark background.
 */
@Composable
fun seriesColors(): List<Color> = if (isSystemInDarkTheme()) {
    listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))
} else {
    listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4))
}

private val PlotHeight = 180.dp

/**
 * A bar per [Bucket]; buckets with several values are drawn stacked, first value at the bottom.
 * Bars start at zero; the numbers down the left side ([axis] turns a value into that short
 * label) mark zero, half and the tallest bar. Tap a bar to select it - the screen shows its numbers.
 */
@Composable
fun BarChart(
    buckets: List<Bucket>,
    colors: List<Color>,
    selected: Int,
    onSelect: (Int) -> Unit,
    axis: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val highest = buckets.maxOfOrNull { it.total } ?: 0.0
    val max = highest.takeIf { it > 0 } ?: 1.0
    val grid = MaterialTheme.colorScheme.outlineVariant
    // The tallest bar stops this far below the top of the plot area.
    val headroom = 8.dp

    Row(modifier.fillMaxWidth()) {
        if (highest > 0) {
            Box(Modifier.height(PlotHeight + headroom).padding(end = 8.dp)) {
                val style = MaterialTheme.typography.labelSmall
                val ink = MaterialTheme.colorScheme.onSurfaceVariant
                // Each number is shifted so its middle sits on the line it belongs to.
                Text(axis(max), Modifier.align(Alignment.TopEnd), style = style, color = ink)
                Text(axis(max / 2), Modifier.align(Alignment.CenterEnd).offset(y = headroom / 2), style = style, color = ink)
                Text(axis(0.0), Modifier.align(Alignment.BottomEnd).offset(y = 6.dp), style = style, color = ink)
            }
        }
        Column(Modifier.weight(1f)) {
            Box {
                // Light guide lines at the tallest bar and at half of it.
                Canvas(Modifier.fillMaxWidth().height(PlotHeight + headroom)) {
                    for (share in listOf(1f, 0.5f)) {
                        val y = size.height - share * PlotHeight.toPx()
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }
                Columns(buckets.size, selected, onSelect) { index ->
                    // Stacked top-down, so the series are reversed to put the first one at the bottom.
                    Column(
                        modifier = Modifier.fillMaxWidth(0.62f).widthIn(max = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val segments = buckets[index].values.withIndex().filter { it.value > 0 }.reversed()
                        segments.forEachIndexed { position, (series, value) ->
                            val top = if (position == 0) 4.dp else 0.dp
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(PlotHeight * (value / max).toFloat())
                                    .background(
                                        colors[series % colors.size],
                                        RoundedCornerShape(topStart = top, topEnd = top),
                                    ),
                            )
                        }
                    }
                }
            }
            Axis(buckets, selected)
        }
    }
}

/**
 * A line through the buckets' (single) values, with a dot on each. Periods without a value
 * are skipped and the line runs straight across them. The vertical scale runs from the
 * lowest to the highest value, so small changes are visible; the numbers down the left side
 * ([axis] turns a value into that short label) say what the top, middle and bottom stand for.
 * Tap a period to select it.
 */
@Composable
fun LineChart(
    buckets: List<Bucket>,
    color: Color,
    selected: Int,
    onSelect: (Int) -> Unit,
    axis: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val points = buckets.withIndex().filter { it.value.total > 0 }
    val low = points.minOfOrNull { it.value.total } ?: 0.0
    val high = points.maxOfOrNull { it.value.total } ?: 0.0
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outlineVariant
    // The plot keeps this much clear above the highest and below the lowest value.
    val inset = 12.dp

    Row(modifier.fillMaxWidth()) {
        // The scale: highest value at the top guide line, lowest at the bottom one.
        if (points.isNotEmpty()) {
            Box(Modifier.height(PlotHeight + 8.dp).padding(end = 8.dp)) {
                val style = MaterialTheme.typography.labelSmall
                val ink = MaterialTheme.colorScheme.onSurfaceVariant
                // Nudged by half a line of text so each number sits level with its guide line.
                val nudge = inset - 8.dp
                if (high > low) {
                    Text(axis(high), Modifier.align(Alignment.TopEnd).padding(top = nudge), style = style, color = ink)
                    Text(axis((low + high) / 2), Modifier.align(Alignment.CenterEnd), style = style, color = ink)
                    Text(axis(low), Modifier.align(Alignment.BottomEnd).padding(bottom = nudge), style = style, color = ink)
                } else {
                    Text(axis(high), Modifier.align(Alignment.CenterEnd), style = style, color = ink)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Box {
                Columns(buckets.size, selected, onSelect) { }
                Canvas(Modifier.fillMaxWidth().height(PlotHeight + 8.dp)) {
                    val edge = inset.toPx()
                    val column = size.width / buckets.size
                    fun x(index: Int) = column * (index + 0.5f)
                    fun y(value: Double): Float {
                        val share = if (high > low) ((value - low) / (high - low)).toFloat() else 0.5f
                        return size.height - edge - share * (size.height - 2 * edge)
                    }
                    // Light guide lines, one for each number on the left.
                    for (guide in listOf(edge, size.height / 2, size.height - edge)) {
                        drawLine(grid, Offset(0f, guide), Offset(size.width, guide), strokeWidth = 1.dp.toPx())
                    }
                    if (points.size > 1) {
                        val path = Path()
                        points.forEachIndexed { position, (index, bucket) ->
                            if (position == 0) path.moveTo(x(index), y(bucket.total)) else path.lineTo(x(index), y(bucket.total))
                        }
                        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    for ((index, bucket) in points) {
                        val centre = Offset(x(index), y(bucket.total))
                        val big = index == selected
                        // A ring in the background colour keeps each dot distinct from the line.
                        drawCircle(surface, (if (big) 8.dp else 6.dp).toPx(), centre)
                        drawCircle(color, (if (big) 6.dp else 4.dp).toPx(), centre)
                    }
                }
            }
            Axis(buckets, selected)
        }
    }
}

/** One tappable column per period; the selected one is shaded. [content] sits at its bottom. */
@Composable
private fun Columns(count: Int, selected: Int, onSelect: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(PlotHeight + 8.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (index == selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.BottomCenter,
            ) { content(index) }
        }
    }
}

/** The baseline and the period names under it. */
@Composable
private fun Axis(buckets: List<Bucket>, selected: Int) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        buckets.forEachIndexed { index, bucket ->
            Text(
                bucket.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (index == selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}
