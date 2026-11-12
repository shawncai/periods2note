package com.period.local.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.period.local.data.ImportPreview
import com.period.local.domain.Period
import com.period.local.domain.PeriodValidation
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun RecordEditor(
    request: EditorRequest, records: List<Period>, today: LocalDate, busy: Boolean,
    onDismiss: () -> Unit, onSave: (Period) -> Unit,
) {
    val original = request.original
    var startText by rememberSaveable { mutableStateOf((original?.start ?: request.initialStart ?: today).toString()) }
    var endText by rememberSaveable { mutableStateOf((original?.end ?: today.takeIf { request.complete })?.toString().orEmpty()) }
    var ended by rememberSaveable { mutableStateOf(endText.isNotEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val id = rememberSaveable { original?.id ?: java.util.UUID.randomUUID().toString() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (request.complete) "记录结束日期" else if (original != null) "修改经期记录" else "补录经期") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DateInput("开始日期", startText, today, !busy, { startText = it; error = null })
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { ended = !ended }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ended, enabled = !busy, onCheckedChange = { ended = it; error = null })
                    Text("已结束，填写结束日期")
                }
                if (ended) DateInput("结束日期", endText, today, !busy, { endText = it; error = null })
                else Text("结束日期暂留空，之后可以补充。", color = Secondary, fontSize = 13.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                try {
                    val record = Period(id, LocalDate.parse(startText.trim()), if (ended) LocalDate.parse(endText.trim()) else null)
                    PeriodValidation.validate(records.filterNot { it.id == id } + record, today)
                    onSave(record)
                } catch (_: java.time.format.DateTimeParseException) {
                    error = "请填写有效日期，格式为 YYYY-MM-DD"
                } catch (invalid: IllegalArgumentException) { error = invalid.message }
            }) { Text(if (busy) "保存中…" else "保存记录") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun DateInput(label: String, value: String, today: LocalDate, enabled: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled,
        label = { Text(label) }, placeholder = { Text("YYYY-MM-DD") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        trailingIcon = {
            IconButton(enabled = enabled, onClick = {
                val initial = runCatching { LocalDate.parse(value) }.getOrDefault(today)
                DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) },
                    initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                    datePicker.maxDate = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                }.show()
            }) { Icon(Icons.Outlined.CalendarMonth, "选择$label") }
        },
    )
}

@Composable
fun ImportDialog(preview: ImportPreview, busy: Boolean, onDismiss: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    var replacements by remember(preview) { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入预览") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${preview.incoming.size}条记录 · 新增${preview.additions.size}条")
                Text("${preview.duplicateCount}条完全重复，将自动跳过", color = Secondary)
                if (preview.conflicts.isNotEmpty()) {
                    Text("${preview.conflicts.size}条存在差异。勾选后采用导入版本，其余保留手机中的记录。", color = Secondary)
                    preview.conflicts.forEach { conflict ->
                        HorizontalDivider(color = Track)
                        Text(conflict.incoming.start.toString(), fontWeight = FontWeight.SemiBold)
                        Text("已有 ${conflict.existing.start} 至 ${conflict.existing.end ?: "待确认"}", fontSize = 12.sp)
                        Text("导入 ${conflict.incoming.start} 至 ${conflict.incoming.end ?: "待确认"}", fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = conflict.incoming.id in replacements, enabled = !busy,
                                onCheckedChange = { checked ->
                                    replacements = if (checked) replacements + conflict.incoming.id else replacements - conflict.incoming.id
                                })
                            Text("采用导入版本", fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onConfirm(replacements) }) { Text(if (busy) "导入中…" else "确认导入") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}
