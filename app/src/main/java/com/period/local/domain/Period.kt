package com.period.local.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Period(
    val id: String,
    val start: LocalDate,
    val end: LocalDate? = null,
) {
    val duration: Int? get() = end?.let { ChronoUnit.DAYS.between(start, it).toInt() + 1 }

    companion object {
        fun create(start: LocalDate, end: LocalDate? = null) = Period(UUID.randomUUID().toString(), start, end)
    }
}

object PeriodValidation {
    fun validate(records: List<Period>, today: LocalDate) {
        require(records.map { it.id }.distinct().size == records.size) { "记录 ID 重复，请检查导入文件" }
        require(records.map { it.start }.distinct().size == records.size) { "同一天已有经期记录" }
        val sorted = records.sortedBy { it.start }
        sorted.forEachIndexed { index, record ->
            require(record.id.isNotBlank()) { "记录 ID 不能为空" }
            require(record.start.year in 1..9999) { "开始日期超出支持范围" }
            require(!record.start.isAfter(today)) { "实际经期开始日期不能晚于今天" }
            record.end?.let { end ->
                require(!end.isBefore(record.start)) { "结束日期不能早于开始日期" }
                require(!end.isAfter(today)) { "实际经期结束日期不能晚于今天" }
                val next = sorted.getOrNull(index + 1)
                require(next == null || end.isBefore(next.start)) { "经期日期与另一条记录重叠" }
            }
        }
    }
}
