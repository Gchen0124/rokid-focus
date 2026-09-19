package com.chenniuniu.rokidfocus.glass.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenniuniu.rokidfocus.glass.data.FocusTask
import com.chenniuniu.rokidfocus.glass.data.GlassState
import com.chenniuniu.rokidfocus.glass.data.Opportunity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
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
private val SelectBg = Color(0xFF00FF66)
private val SelectInk = Color(0xFF001408)
private val SlotBg = Color(0xFF071F12)
private val Gold = Color(0xFFFFE08A)
private const val CutoffHour = 17
private const val CutoffMinute = 30
private const val DailySlogan = "怪奇实验室 + 外交官"

private val RingFills = listOf(
    Color(0xFF00FF88),
    Color(0xCC00E070),
    Color(0x9900C060),
    Color(0x6600A050),
)

@Composable
fun GlassHud(
    state: GlassState,
    onToggleListen: () -> Unit = {},
    onShowResults: (Boolean) -> Unit = {},
) {
    val now = ZonedDateTime.now()
    val rows = FocusTask.open(state.tasks).take(11)
    val finished = FocusTask.finishedCount(state.tasks)
    val cap = capacity(now, rows)
    val cdText = if (state.checkInActive || onFive(now)) "CHECK" else state.countdownLabel.ifBlank { countdown(now) }
    var dragX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(state.showResults) {
                detectTapGestures(onTap = { onToggleListen() })
            }
            .pointerInput(state.showResults) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(dragX) > 36f) onShowResults(dragX < 0f)
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, dx -> dragX += dx },
                )
            }
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
            if (!state.convoActive && !state.listenOn) {
                TimeRings(now = now, modifier = Modifier.size(118.dp))
            }
        }

        if (state.agentActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(SlotBg)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    "AGENT",
                    color = Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                if (state.agentLine.isNotBlank()) {
                    Text(
                        state.agentLine,
                        color = Cap,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.agentImg) {
                    Text(
                        "image · phone",
                        color = Dim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (state.convoActive) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Only the sentence being spoken / just spoken. Full history stays
                    // in the phone app; the waveguide is a glance, not a log.
                    val liveText = state.convoLine.trim()
                    if (liveText.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(),
                        ) {
                            TurnRow(
                                Turn(
                                    who = state.convoWho.ifBlank { "them" },
                                    text = liveText,
                                    trans = state.convoTrans.trim(),
                                ),
                                newest = true,
                            )
                        }
                    }
                }
                ReactMenu(
                    drafts = state.convoDrafts,
                    pick = state.convoPick,
                    pulse = (state.clockLabel.lastOrNull()?.code ?: 0) % 2 == 0,
                )
            }
        } else if (state.showResults) {
            Text(
                text = state.slogan.ifBlank { DailySlogan },
                color = Slogan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
            Text("results", color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            val withOut = rows.filter { it.outcome.isNotBlank() }.take(4)
            if (withOut.isEmpty()) {
                Text("no outcomes yet", color = Mid, fontSize = 13.sp)
            } else {
                withOut.forEach { task ->
                    Text(
                        text = task.outcome,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("cal", color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            val cal = Opportunity.upcoming(state.opportunities).take(7)
            if (cal.isEmpty()) {
                Text("no upcoming", color = Mid, fontSize = 13.sp)
            } else {
                cal.forEach { o ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = o.dateLabel(),
                            color = Cap,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = o.name,
                            color = Green,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else if (rows.isEmpty()) {
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
            rows.forEach { task ->
                ValueRow(
                    task = task,
                    ddl = ddlLabel(task.idealAt),
                    scale = valueScale(task.usd, maxUsd),
                    showResult = false,
                )
            }
        }

        if (!state.convoActive) Spacer(Modifier.weight(1f))
        if (state.listenLine.isNotBlank()) {
            Text(
                text = "mic ${state.listenLine}",
                color = Dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
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
            Text(
                text = when {
                    state.listenLine == "live" -> "tap=mic off"
                    else -> "tap=listen"
                },
                color = Dim,
                fontSize = 11.sp,
            )
            if (!state.convoActive) {
                Text(
                    if (state.showResults) "swipe=tasks" else "swipe=results+cal",
                    color = Dim,
                    fontSize = 11.sp,
                )
            }
            if (finished > 0) {
                Spacer(Modifier.width(8.dp))
                Text("$finished done", color = Mid, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("back=exit", color = Dim, fontSize = 11.sp)
        }
    }
}

private val SlotKey = listOf("A", "B", "C")
private val SlotTag = listOf("LEAN", "TURN", "SKIP")

/**
 * One transcript turn. YOU (glasses mic) aligns left, THEY (phone mic) aligns
 * right. The translation, when the speech was not the mother tongue, sits on
 * the line under the original.
 */
private data class Turn(val who: String, val text: String, val trans: String)

private fun roleLabel(who: String): String = when {
    who == "you" -> "YOU"
    who.startsWith("them") && who.length > 4 -> "S${who.removePrefix("them")}"
    who.startsWith("them") -> "THEY"
    else -> "THEY"
}

@Composable
private fun TurnRow(turn: Turn, newest: Boolean) {
    val fromThem = turn.who != "you"
    val align = if (fromThem) TextAlign.End else TextAlign.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromThem) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = "${roleLabel(turn.who)}  ${turn.text}",
            color = if (newest) Green else Mid,
            fontSize = if (newest) 16.sp else 13.sp,
            fontWeight = if (newest) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            lineHeight = if (newest) 18.sp else 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (turn.trans.isNotBlank()) {
            Text(
                text = turn.trans,
                color = if (newest) Cap else Dim,
                fontSize = if (newest) 13.sp else 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
                lineHeight = if (newest) 15.sp else 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReactMenu(drafts: List<String>, pick: Int, pulse: Boolean) {
    val rolling = drafts.size == 1 && drafts[0] == "…"
    val rows = if (rolling) emptyList() else drafts.take(3)
    if (rows.isEmpty()) {
        Text(
            text = if (pulse) "listening…" else "listening   ",
            color = Dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(SlotBg)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        return
    }
    rows.forEachIndexed { i, line ->
        val on = i == pick
        val key = SlotKey.getOrElse(i) { "${i + 1}" }
        val tag = if (line.equals("skip", true)) "SKIP" else SlotTag.getOrElse(i) { "SAY" }
        val cursor = when {
            on && pulse -> "▶"
            on -> "▷"
            else -> " "
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
                .background(if (on) SelectBg else SlotBg)
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$cursor [$key]",
                    color = if (on) SelectInk else Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                val parts = line.split(" | ", limit = 2)
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = parts[0],
                        color = if (on) SelectInk else if (tag == "SKIP") Dim else Mid,
                        fontSize = if (on) 15.sp else 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                    if (parts.size > 1) {
                        Text(
                            text = parts[1],
                            color = if (on) SelectInk else Dim,
                            fontSize = if (on) 10.sp else 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueRow(task: FocusTask, ddl: String, scale: Float, showResult: Boolean) {
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
            text = ddl,
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

private fun parseIdeal(iso: String): ZonedDateTime? =
    runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()) }.getOrNull()

private fun ddlLabel(iso: String): String {
    val t = parseIdeal(iso) ?: return "—"
    return clock12(t)
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
    val finish = tasks.mapNotNull { parseIdeal(it.idealAt) }.maxOrNull()
    val ddl = finish?.let { clock12(it) } ?: "—"
    if (need <= 0.0) return CapLine("no work queued", false)
    if (finish == null) {
        return CapLine("need ${FocusTask.formatMinutes(need)}  ·  ddl —", false)
    }
    val slack = java.time.Duration.between(finish, cut).toMinutes().toInt()
    if (slack >= 0) {
        return CapLine(
            "need ${FocusTask.formatMinutes(need)}  ·  ddl $ddl  ·  ${FocusTask.formatMinutes(slack)} slack vs 5:30",
            false
        )
    }
    return CapLine(
        "need ${FocusTask.formatMinutes(need)}  ·  ddl $ddl  ·  over 5:30 by ${FocusTask.formatMinutes(-slack)}",
        true
    )
}
