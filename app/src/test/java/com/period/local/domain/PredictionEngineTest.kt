package com.period.local.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PredictionEngineTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun records(cycles: List<Int>, first: LocalDate = LocalDate.of(2020, 1, 1)): List<Period> {
        var date = first
        val starts = mutableListOf(date)
        cycles.forEach { date = date.plusDays(it.toLong()); starts.add(date) }
        return starts.mapIndexed { i, start -> Period("record-$i", start, start.plusDays(6)) }
    }

    @Test fun stableSyntheticHistoryHasExactPredictionAndZeroError() {
        val cycles = List(24) { 30 }
        val input = records(cycles)
        val result = PredictionEngine.calculate(input)!!
        assertEquals(24, result.totalCycleCount)
        assertEquals(12, result.sampleCount)
        assertEquals(30, result.cycleDays)
        assertEquals(input.last().start.plusDays(30), result.estimatedStart)
        assertEquals(18, result.backtests.size)
        assertEquals(0.0, result.recentMeanAbsoluteError!!, 0.00001)
        assertEquals(cycles, PredictionEngine.intervals(input))
    }

    @Test fun eachBacktestUsesOnlyItsHistoricalPrefix() {
        val input = records(listOf(28,30,32,35,29,38,14,87,40,41,31,36,33))
        val result = PredictionEngine.calculate(input)!!
        result.backtests.forEachIndexed { index, point ->
            val prefix = input.take(index + 7)
            assertEquals(PredictionEngine.calculate(prefix)!!.estimatedStart, point.predictedStart)
        }
    }

    @Test fun sparseHistoryAndMinimumRangeAreHandled() {
        assertNull(PredictionEngine.calculate(emptyList()))
        assertNull(PredictionEngine.calculate(records(listOf(28,28))))
        val result = PredictionEngine.calculate(records(List(3) { 28 }))!!
        assertEquals(result.estimatedStart.minusDays(3), result.windowStart)
        assertEquals(result.estimatedStart.plusDays(3), result.windowEnd)
        assertNull(result.meanAbsoluteError)
    }

    @Test fun unusualCyclesAreRetainedAndRecentChangesAdjustForecast() {
        assertEquals(55, PredictionEngine.weightedCycle(listOf(14,87)))
        val before = PredictionEngine.weightedCycle(List(12) { 28 })
        val after = PredictionEngine.weightedCycle(List(8) { 28 } + List(4) { 40 })
        assertTrue(after > before)
        assertTrue(after < 40)
    }

    @Test fun quantileUsesNearestRankAndMedianUsesCompletedRecentPeriods() {
        assertEquals(8, PredictionEngine.percentile80((1..10).toList()))
        assertEquals(3, PredictionEngine.percentile80(listOf(1,2,3)))
        val input = records(List(7) { 30 }).mapIndexed { i, p -> p.copy(end = p.start.plusDays(i.toLong())) }
        assertEquals(5, PredictionEngine.duration(input.dropLast(1) + input.last().copy(end = null)))
    }

    @Test fun calendarArithmeticHandlesLeapDayAndYearBoundary() {
        val leap = PredictionEngine.calculate(records(List(3) { 29 }, LocalDate.of(2023, 11, 5)))!!
        assertEquals(LocalDate.of(2024, 2, 29), leap.estimatedStart)
        val year = PredictionEngine.calculate(records(List(3) { 30 }, LocalDate.of(2025, 9, 15)))!!
        assertEquals(LocalDate.of(2026, 1, 13), year.estimatedStart)
    }

    @Test fun reorderedAndEditedHistoryRecomputesDeterministically() {
        val input = records(List(12) { 30 })
        assertEquals(PredictionEngine.calculate(input), PredictionEngine.calculate(input.reversed()))
        val edited = input.dropLast(1) + input.last().copy(start = input.last().start.plusDays(10), end = null)
        assertTrue(PredictionEngine.calculate(edited)!!.estimatedStart > PredictionEngine.calculate(input)!!.estimatedStart)
    }

    @Test fun validatesOverlapDuplicatesFutureAndReverseDates() {
        val original = Period("a", today.minusDays(10), today.minusDays(5))
        assertThrows(IllegalArgumentException::class.java) { PeriodValidation.validate(listOf(original, original.copy(id = "b")), today) }
        assertThrows(IllegalArgumentException::class.java) { PeriodValidation.validate(listOf(original, Period("b", today.minusDays(7))), today) }
        assertThrows(IllegalArgumentException::class.java) { PeriodValidation.validate(listOf(Period("a", today.plusDays(1))), today) }
        assertThrows(IllegalArgumentException::class.java) { PeriodValidation.validate(listOf(original.copy(end = original.start.minusDays(1))), today) }
        PeriodValidation.validate(listOf(original.copy(end = null)), today)
    }
}
