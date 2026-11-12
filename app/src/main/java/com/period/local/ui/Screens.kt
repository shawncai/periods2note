package com.period.local.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.period.local.BuildConfig
import com.period.local.ScreenState
import com.period.local.domain.Period
import com.period.local.domain.PredictionEngine
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val shortDate = DateTimeFormatter.ofPattern("M月d日")
private val fullDate = DateTimeFormatter.ofPattern("yyyy年M月d日")
fun LocalDate.shortText(): String = format(shortDate)
fun dateRange(start: LocalDate, end: LocalDate): String =
    if (start.year == end.year) "${start.shortText()}—${end.shortText()}"
    else "${start.format(fullDate)}—${end.format(fullDate)}"

@Composable
fun TodayScreen(
    state: ScreenState, busy: Boolean, onSettings: () -> Unit,
    onEndToday: () -> Unit, onStartToday: () -> Unit,
) {
    val latest = state.records.lastOrNull()
    val prediction = state.prediction
    val days = prediction?.let { ChronoUnit.DAYS.between(state.today, it.estimatedStart).toInt() }
    val cycleDay = latest?.let { ChronoUnit.DAYS.between(it.start, state.today).toInt() + 1 }
    val weekday = state.today.format(DateTimeFormatter.ofPattern("EEEE", Locale.SIMPLIFIED_CHINESE))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader("今天", "${state.today.shortText()} · $weekday", onSettings)
        Spacer(Modifier.height(28.dp))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth - 38.dp, 300.dp)
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2 + 5.dp.toPx()
                    val radius = (size.minDimension - 2 * inset) / 2
                    val origin = Offset(inset, inset)
                    val ringSize = Size(radius * 2, radius * 2)
                    drawArc(Track, -84f, 348f, false, origin, ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (prediction != null && cycleDay != null) {
                        val fraction = (cycleDay.toFloat() / prediction.cycleDays).coerceIn(0.02f, 1f)
                        val sweep = 348f * fraction
                        drawArc(Accent, -84f, sweep, false, origin, ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        val angle = Math.toRadians((-84f + sweep).toDouble())
                        val point = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                        drawCircle(Color.White, 10.dp.toPx(), point)
                        drawCircle(Accent, 6.5.dp.toPx(), point)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 27.dp)) {
                    Text(when {
                        days == null -> "经期预测"
                        days < 0 -> "已超过预计日期"
                        days == 0 -> "预计今天开始"
                        else -> "距离下次经期"
                    }, fontSize = 18.sp, textAlign = TextAlign.Center, color = Ink)
                    if (days != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(kotlin.math.abs(days).toString(), fontSize = 82.sp, fontWeight = FontWeight.SemiBold, lineHeight = 94.sp)
                            Text("天", fontSize = 30.sp, modifier = Modifier.padding(bottom = 10.dp, start = 5.dp))
                        }
                        Text("预计 ${prediction.estimatedStart.shortText()}", fontSize = 16.sp, color = Secondary)
                    } else {
                        Text("数据积累中", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 20.dp))
                        Text("记录至少 3 个完整周期", fontSize = 13.sp, color = Secondary)
                    }
                }
                cycleDay?.let {
                    Text("周期第${it}天", fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = 2.dp)
                            .background(Pale, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        Text(
            prediction?.let { "参考范围 ${dateRange(it.windowStart, it.windowEnd)}" } ?: "持续记录，了解自己的周期",
            color = Secondary, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp),
        )
        Column(
            Modifier.fillMaxWidth().background(Pale, RoundedCornerShape(24.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (latest?.end == null) "本次经期" else "最近一次经期", fontSize = 16.sp)
            Text(latest?.let { "${it.start.shortText()}开始" } ?: "开始你的第一条记录",
                fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Text(latest?.end?.let { "${it.shortText()}结束 · 持续${latest.duration}天" }
                ?: if (latest == null) "记录后即可查看周期变化" else "结束日期待确认", color = Secondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
        val canStart = latest == null || (latest.end != null && latest.end < state.today)
        val canEnd = latest != null && latest.end == null
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onStartToday, enabled = !busy && canStart,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(20.dp),
            ) { Text("今天开始", fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            Button(
                onClick = onEndToday, enabled = !busy && canEnd,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(20.dp),
            ) { Text("今天结束", fontSize = 17.sp, fontWeight = FontWeight.Medium) }
        }
        Text(
            when {
                canEnd -> "点击结束，将记录今天为结束日期"
                canStart -> "点击开始，将记录今天为开始日期"
                else -> "今天的经期已记录"
            }, color = Secondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
fun CalendarScreen(state: ScreenState, onSettings: () -> Unit, onEdit: (Period?) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(state.today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(state.today.toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val prediction = state.prediction
    val record = state.records.find { selected >= it.start && selected <= (it.end ?: it.start) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        PageHeader("日历", "查看、补录和修改经期", onSettings)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月") }
            Text("${month.year}年${month.monthValue}月", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f).padding(vertical = 12.dp), textAlign = TextAlign.Center, color = Secondary)
            }
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val cells = ((firstOffset + month.lengthOfMonth() + 6) / 7) * 7
        for (week in 0 until cells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (dayOfWeek in 0..6) {
                    val day = week * 7 + dayOfWeek - firstOffset + 1
                    if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(49.dp))
                    else {
                        val date = month.atDay(day)
                        val actual = state.records.any { date >= it.start && date <= (it.end ?: it.start) }
                        val predicted = prediction != null && date >= prediction.estimatedStart &&
                            date < prediction.estimatedStart.plusDays((prediction.estimatedDuration ?: 1).toLong())
                        val inWindow = prediction != null && date >= prediction.windowStart && date <= prediction.windowEnd
                        val color = when { actual -> Accent; predicted -> Color(0xFFEAF1FC); else -> Color.Transparent }
                        Box(Modifier.weight(1f).height(49.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(43.dp).background(color, CircleShape)
                                    .then(if (date == selected || date == state.today) Modifier.border(1.5.dp, if (actual) Ink else Accent, CircleShape) else Modifier)
                                    .clickable { selectedText = date.toString() }
                                    .semantics { contentDescription = "${date.format(fullDate)}${if (actual) "，已记录经期" else if (predicted) "，预测经期" else ""}" },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (predicted && !actual) Canvas(Modifier.fillMaxSize()) {
                                    drawCircle(Accent, radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
                                }
                                Text(day.toString(), color = if (actual) Color.White else Ink, fontSize = 16.sp)
                                if (inWindow && !actual) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(3.dp).background(Accent, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        Text("实色 已记录    虚线 预计经期    蓝点 开始参考范围", color = Secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        TextButton(onClick = { monthText = YearMonth.from(state.today).toString(); selectedText = state.today.toString() }) { Text("回到今天") }
        HorizontalDivider(color = Track, modifier = Modifier.padding(vertical = 12.dp))
        Text(selected.format(fullDate), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        if (record != null) {
            Text("${record.start.shortText()}开始 · ${record.end?.let { "${it.shortText()}结束" } ?: "结束日期待确认"}", color = Secondary, modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = { onEdit(record) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("修改这次记录") }
        } else {
            Text(if (selected > state.today) "未来日期用于查看预测" else "这一天没有已记录的经期", color = Secondary, modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = { onEdit(Period("", selected)) }, enabled = selected <= state.today, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("补录这一天") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun TrendsScreen(state: ScreenState, onSettings: () -> Unit) {
    val prediction = state.prediction
    val cycles = PredictionEngine.intervals(state.records)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        PageHeader("趋势", "${state.records.size}次记录 · ${cycles.size}个完整周期", onSettings)
        Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("近期周期估计", prediction?.cycleDays?.toString() ?: "—", "天")
            Metric("经期持续估计", PredictionEngine.duration(state.records)?.toString() ?: "—", "天")
        }
        Text("最近12个周期", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        val recent = cycles.takeLast(12)
        if (recent.isEmpty()) Text("记录下一次开始日期后，这里会显示周期变化", color = Secondary, modifier = Modifier.padding(vertical = 24.dp))
        else {
            Row(Modifier.fillMaxWidth().height(148.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                recent.forEachIndexed { index, days ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(days.toString(), fontSize = 10.sp, color = Secondary)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.fillMaxWidth().height((90f * days / recent.max()).dp)
                            .background(if (index == recent.lastIndex) Accent else Color(0xFFCADAF5), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    }
                }
            }
            Text("较早                                                     最近", fontSize = 11.sp, color = Secondary, modifier = Modifier.fillMaxWidth().padding(top = 7.dp))
        }
        HorizontalDivider(color = Track, modifier = Modifier.padding(vertical = 24.dp))
        Text("预测表现", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        val recentError = prediction?.recentMeanAbsoluteError
        Text(if (recentError == null) "正在积累回测数据" else "最近${minOf(12, prediction.backtests.size)}次平均误差 ${String.format(Locale.ROOT, "%.1f", recentError)} 天",
            fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
        Text(prediction?.meanAbsoluteError?.let { "全部${prediction.backtests.size}次回测平均误差 ${String.format(Locale.ROOT, "%.1f", it)} 天" }
            ?: "至少6个历史周期后开始滚动回测", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
        Text("参考范围根据历史误差计算，会随真实记录更新。", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
        HorizontalDivider(color = Track, modifier = Modifier.padding(vertical = 24.dp))
        Text("历史记录", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        state.records.asReversed().forEach { period ->
            Row(Modifier.fillMaxWidth().padding(vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(period.start.format(fullDate), fontSize = 16.sp)
                    Text(period.end?.let { "至${it.shortText()} · ${period.duration}天" } ?: "结束日期待确认", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            HorizontalDivider(color = Track, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    Column {
        Text(label, fontSize = 13.sp, color = Secondary)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Text(value, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
            Text(unit, fontSize = 15.sp, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp), color = Secondary)
        }
    }
}

@Composable
fun SettingsScreen(count: Int, busy: Boolean, onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Text("设置与备份", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(30.dp))
        Text("本地记录", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text("已保存 $count 条经期记录", color = Secondary, modifier = Modifier.padding(vertical = 12.dp))
        Text("记录和预测保存在此手机中。备份可用于换机或恢复记录。", color = Secondary, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(10.dp)); Text("导出本地备份")
        }
        OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(10.dp)); Text("导入备份 / 美柚记录")
        }
        Text("导出的 JSON 文件包含完整日期记录，可直接阅读。请选择自己的本地文件夹保存。", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
        HorizontalDivider(color = Track, modifier = Modifier.padding(vertical = 30.dp))
        Text("关于预测", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text("根据最近最多12个真实周期加权计算。新记录保存后，预计日期与参考范围会自动更新。", color = Secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        Text("预测用于安排日常生活。", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Text("经期记录 ${BuildConfig.VERSION_NAME}", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 32.dp))
    }
}
