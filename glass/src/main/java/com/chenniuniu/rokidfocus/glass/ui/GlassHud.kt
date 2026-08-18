package com.chenniuniu.rokidfocus.glass.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenniuniu.rokidfocus.glass.data.FocusTask
import com.chenniuniu.rokidfocus.glass.data.GlassState
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

private val Green = Color(0xFF00FF66)
private val Mid = Color(0xFF8CFFC4)
private val Dim = Color(0xFF3D7A58)
private val Stamp = Color(0xFF9FD9B3)
private val Usd = Color(0xFFC8FF9A)
private val Mins = Color(0xFF8CFFC4)
private val Cap = Color(0xFFC8F5D8)
private val Over = Color(0xFFE8FFD0)
private val Track = Color(0xFF163322)
private val Slogan = Color(0xFFFFFFFF)
private const val CutoffHour = 17
private const val CutoffMinute = 30
private const val DailySlogan = "怪奇实验室"

private val RingFills = listOf(
    Color(0xFF00FF88),
    Color(0xCC00E070),
    Color(0x9900C060),
    Color(0x6600A050),
)

@Composable
fun GlassHud(state: GlassState, onDim: () -> Unit = {}) {
    val now = ZonedDateTime.now()
    val rows = FocusTask.open(state.tasks).take(11)
    val finished = FocusTask.finishedCount(state.tasks)
    val cap = capacity(now, rows)
    val cdText = if (state.checkInActive || onFive(now)) "CHECK" else state.countdownLabel.ifBlank { countdown(now) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDim,
            )
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp, end = 4.dp)
            ) {
                Text(
                    text = stamp(now),
                    color = Stamp,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    lineHeight = 13.sp,
                    overflow = TextOverflow.Clip
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = clock12(now),
                        color = Green,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Text(
                        text = "  $cdText",
                        color = if (state.checkInActive || onFive(now)) Color.White else Mid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
            TimeRings(now = now, modifier = Modifier.size(118.dp))
        }

        Text(
            text = DailySlogan,
            color = Slogan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )

        if (rows.isEmpty()) {
            Text(
                text = if (finished > 0) "cleared · $finished done" else "Empty list",
                color = Mid,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        } else {
            Spacer(Modifier.height(4.dp))
            val maxUsd = rows.maxOf { it.usd }
            val showResult = state.showResults
            var acc = 0
            rows.forEach { task ->
                acc += task.minutes
                ValueRow(
                    task = task,
                    etf = clock12(now.plusMinutes(acc.toLong())),
                    scale = valueScale(task.usd, maxUsd),
                    showResult = showResult && task.outcome.isNotBlank(),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = cap.text,
            color = if (cap.over) Over else Cap,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 3.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(if (state.showResults) "swipe=tasks" else "swipe=results", color = Dim, fontSize = 11.sp)
            if (finished > 0) {
                Spacer(Modifier.width(8.dp))
                Text("$finished done", color = Mid, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("back=exit", color = Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ValueRow(task: FocusTask, etf: String, scale: Float, showResult: Boolean) {
    val titleSp = (10f + scale * 10f).sp
    val metaSp = (9f + scale * 6f).sp
    val vPad = (1f + scale * 3.5f).dp
    if (showResult) {
        Text(
            text = task.outcome,
            color = Color.White,
            fontSize = titleSp,
            fontWeight = FontWeight.Bold,
            lineHeight = (titleSp.value + 2f).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = vPad)
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = vPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = task.moneyLabel(),
            color = Usd,
            fontSize = titleSp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width((40f + scale * 16f).dp),
            maxLines = 1
        )
        Text(
            text = task.minsLabel(),
            color = Mins,
            fontSize = metaSp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width((28f + scale * 10f).dp),
            maxLines = 1
        )
        Text(
            text = task.title,
            color = Green,
            fontSize = titleSp,
            fontWeight = if (scale > 0.85f) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = etf,
            color = Cap,
            fontSize = metaSp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/** 0 = cheapest / $0, 1 = highest USD in the current list. Log so $500k vs $100 still separates. */
private fun valueScale(usd: Double, maxUsd: Double): Float {
    if (maxUsd <= 0.0) return 0f
    val t = ln(usd.coerceAtLeast(0.0) + 1.0) / ln(maxUsd + 1.0)
    return t.toFloat().coerceIn(0f, 1f)
}

@Composable
private fun TimeRings(now: ZonedDateTime, modifier: Modifier = Modifier) {
    val layers = layers(now)
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = size.minDimension / 176f
        val stroke = 8f * scale
        val radii = floatArrayOf(22f, 38f, 54f, 70f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 8.5f * scale
            isFakeBoldText = true
        }

        layers.forEachIndexed { i, layer ->
            val r = radii[i] * scale
            val frac = layer.frac.coerceIn(0.02f, 0.98f)
            val color = RingFills[i]
            drawCircle(
                color = Track,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = frac * 360f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            val (sx, sy) = polar(cx, cy, r, -98.0)
            paint.color = android.graphics.Color.argb(
                (color.alpha * 255).roundToInt(),
                (color.red * 255).roundToInt(),
                (color.green * 255).roundToInt(),
                (color.blue * 255).roundToInt(),
            )
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(layer.abbr, sx, sy + paint.textSize * 0.35f, paint)
            val (ex, ey) = polar(cx, cy, r + 16f * scale, -90.0 + frac * 360.0)
            paint.textAlign = Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "${(layer.frac * 100).roundToInt()}%",
                ex,
                ey + paint.textSize * 0.35f,
                paint
            )
        }
    }
}

private data class RingLayer(val abbr: String, val frac: Float)

private data class CapLine(val text: String, val over: Boolean)

private fun onFive(now: ZonedDateTime): Boolean =
    now.minute % 5 == 0 && now.second < 12

private fun countdown(now: ZonedDateTime): String {
    val rem = now.minute % 5
    val add = if (rem == 0 && now.second == 0) 5 else 5 - rem
    val next = now.withSecond(0).withNano(0).plusMinutes(add.toLong())
    val sec = java.time.Duration.between(now, next).seconds.coerceAtLeast(0)
    return "${sec / 60}:${"%02d".format(sec % 60)}"
}

private fun clock12(now: ZonedDateTime): String {
    var h = now.hour
    val ap = if (h >= 12) "PM" else "AM"
    h %= 12
    if (h == 0) h = 12
    return "$h:${"%02d".format(now.minute)} $ap"
}

private fun stamp(now: ZonedDateTime): String {
    val locale = Locale.US
    val day = now.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val month = now.month.getDisplayName(TextStyle.FULL, locale)
    val week = now.get(WeekFields.ISO.weekOfWeekBasedYear())
    val q = (now.monthValue - 1) / 3 + 1
    return "$day, $month ${ordinal(now.dayOfMonth)}, W$week, Q$q, ${now.year}"
}

private fun ordinal(n: Int): String {
    val v = n % 100
    if (v in 11..13) return "${n}th"
    return when (n % 10) {
        1 -> "${n}st"
        2 -> "${n}nd"
        3 -> "${n}rd"
        else -> "${n}th"
    }
}

private fun jsDay(dow: DayOfWeek): Int =
    if (dow == DayOfWeek.SUNDAY) 0 else dow.value

private fun layers(now: ZonedDateTime): List<RingLayer> {
    val h = now.hour + now.minute / 60f + now.second / 3600f
    val day = jsDay(now.dayOfWeek)
    val daysInMonth = now.toLocalDate().lengthOfMonth()
    val firstJs = jsDay(now.withDayOfMonth(1).dayOfWeek)
    val week = ((now.dayOfMonth + firstJs - 1) / 7) + 1
    val weeks = ((daysInMonth + firstJs - 1) / 7) + 1
    val month = now.monthValue
    return listOf(
        RingLayer("hod", h / 24f),
        RingLayer("dow", (day + h / 24f) / 7f),
        RingLayer("wom", week / weeks.toFloat()),
        RingLayer("moy", (month - 1 + now.dayOfMonth / daysInMonth.toFloat()) / 12f),
    )
}

private fun polar(cx: Float, cy: Float, r: Float, deg: Double): Pair<Float, Float> {
    val a = Math.toRadians(deg)
    return (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
}

private fun capacity(now: ZonedDateTime, tasks: List<FocusTask>): CapLine {
    val need = tasks.sumOf { it.minutes }
    val cut = now.withHour(CutoffHour).withMinute(CutoffMinute).withSecond(0).withNano(0)
    val toCut = java.time.Duration.between(now, cut).toMinutes().toInt()
    val etf = if (need == 0) "" else clock12(now.plusMinutes(need.toLong()))
    if (need == 0) return CapLine("no work queued", false)
    if (toCut <= 0) {
        return CapLine("past 5:30 · ${FocusTask.formatMinutes(need)} still open  ·  ETF $etf", true)
    }
    if (need <= toCut) {
        return CapLine(
            "need ${FocusTask.formatMinutes(need)}  ·  ETF $etf  ·  ${FocusTask.formatMinutes(toCut - need)} free before 5:30",
            false
        )
    }
    return CapLine(
        "need ${FocusTask.formatMinutes(need)}  ·  ETF $etf  ·  over 5:30 by ${FocusTask.formatMinutes(need - toCut)}",
        true
    )
}
