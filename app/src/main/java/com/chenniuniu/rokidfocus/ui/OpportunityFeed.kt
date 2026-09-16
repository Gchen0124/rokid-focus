package com.chenniuniu.rokidfocus.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenniuniu.rokidfocus.data.Opportunity
import java.time.LocalDate

private val Green = Color(0xFF39FF8E)
private val Ink = Color(0xFF050505)
private val Note = Color(0xFFC8F5D8)
private val Dim = Color(0xFF3D7A58)
private val Past = Color(0xFFFFB4B4)
private val Gold = Color(0xFFFFE08A)
private val XBlue = Color(0xFF9AD4FF)

@Composable
fun OpportunityFeed(
    items: List<Opportunity>,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val rows = Opportunity.forSwipe(items, today)
    if (rows.isEmpty()) {
        Box(modifier.background(Ink), contentAlignment = Alignment.Center) {
            Text("No opportunities yet", color = Green, fontSize = 20.sp)
        }
        return
    }
    val start = Opportunity.startIndex(rows, today)
    val pager = rememberPagerState(initialPage = start, pageCount = { rows.size })
    LaunchedEffect(rows.size, start) {
        if (pager.currentPage != start && pager.currentPage == 0) {
            pager.scrollToPage(start)
        }
    }
    VerticalPager(
        state = pager,
        modifier = modifier.background(Ink),
        beyondViewportPageCount = 1,
    ) { page ->
        OpportunityCard(rows[page], today, page + 1, rows.size)
    }
}

@Composable
private fun OpportunityCard(
    item: Opportunity,
    today: LocalDate,
    index: Int,
    total: Int,
) {
    val ctx = LocalContext.current
    val past = item.isPast(today)
    val todayOn = item.isToday(today)
    val badgeColor = when {
        past -> Past
        todayOn -> Color.White
        else -> Gold
    }
    fun open(url: String) {
        if (url.isBlank()) return
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = item.badge(today),
            color = badgeColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = item.kind.ifBlank { "opportunity" }.uppercase(),
            color = Dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = item.dateLabel(),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = item.name,
            color = Green,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (item.note.isNotBlank()) {
            Text(
                text = item.note,
                color = Note,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (item.url.isNotBlank()) {
            TextButton(onClick = { open(item.url) }, modifier = Modifier.fillMaxWidth()) {
                Text("website", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
            }
        }
        if (item.x.isNotBlank()) {
            TextButton(onClick = { open(item.x) }, modifier = Modifier.fillMaxWidth()) {
                Text(item.xHandle(), color = XBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
            }
        }
        TextButton(
            onClick = {
                runCatching { ctx.startActivity(item.calendarInsertIntent()) }
                    .onFailure { open(item.googleCalendarUrl()) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(Color(0xFF0E2A1C), RoundedCornerShape(999.dp)),
        ) {
            Text("add to calendar", color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "$index / $total  ·  swipe  ·  past stays in the deck",
            color = Dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
