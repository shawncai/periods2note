package com.period.local

import com.period.local.domain.Period
import java.time.LocalDate

/** Invented test data, independent of any person's records. Never included in release APKs. */
object SyntheticData {
    fun records(today: LocalDate): List<Period> {
        val intervals = listOf(28, 29, 31, 27, 30, 28, 32, 29, 30, 27, 31, 28, 29, 30, 29)
        var start = today.minusDays(2 + intervals.sum().toLong())
        val dates = mutableListOf(start)
        intervals.forEach { start = start.plusDays(it.toLong()); dates.add(start) }
        return dates.mapIndexed { index, date ->
            Period("demo-$index", date, if (index == dates.lastIndex) null else date.plusDays(4))
        }
    }
}
