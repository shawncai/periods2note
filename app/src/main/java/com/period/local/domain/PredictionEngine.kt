package com.period.local.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class BacktestPoint(val actualStart: LocalDate, val predictedStart: LocalDate, val absoluteError: Int)

data class Prediction(
    val estimatedStart: LocalDate,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val cycleDays: Int,
    val estimatedDuration: Int?,
    val sampleCount: Int,
    val totalCycleCount: Int,
    val meanAbsoluteError: Double?,
    val recentMeanAbsoluteError: Double?,
    val backtests: List<BacktestPoint>,
    val algorithmVersion: String = "weighted-12-v1",
)

object PredictionEngine {
    fun intervals(records: List<Period>): List<Int> = records.sortedBy { it.start }
        .zipWithNext { a, b -> ChronoUnit.DAYS.between(a.start, b.start).toInt() }

    fun weightedCycle(intervals: List<Int>): Int {
        require(intervals.isNotEmpty() && intervals.all { it > 0 })
        val recent = intervals.takeLast(12)
        val weights = recent.indices.map { 0.8.pow(recent.lastIndex - it) }
        return (recent.indices.sumOf { recent[it] * weights[it] } / weights.sum()).roundToInt()
    }

    fun percentile80(values: List<Int>): Int {
        require(values.isNotEmpty())
        return values.sorted()[ceil(values.size * 0.8).toInt() - 1]
    }

    fun duration(records: List<Period>): Int? {
        val values = records.sortedBy { it.start }.mapNotNull { it.duration }.takeLast(6).sorted()
        if (values.isEmpty()) return null
        val middle = values.size / 2
        return if (values.size % 2 == 0) ((values[middle - 1] + values[middle]) / 2.0).roundToInt()
        else values[middle]
    }

    fun calculate(records: List<Period>): Prediction? {
        val sorted = records.sortedBy { it.start }
        val cycles = intervals(sorted)
        if (cycles.size < 3) return null
        require(cycles.all { it > 0 })
        val predictedDays = weightedCycle(cycles)
        val backtests = (6 until cycles.size).map { index ->
            val predicted = sorted[index].start.plusDays(weightedCycle(cycles.take(index)).toLong())
            val actual = sorted[index + 1].start
            BacktestPoint(actual, predicted, abs(ChronoUnit.DAYS.between(predicted, actual)).toInt())
        }
        val halfWidth = if (backtests.size >= 5) {
            max(3, percentile80(backtests.takeLast(20).map { it.absoluteError }))
        } else {
            max(3, cycles.maxOf { abs(it - predictedDays) })
        }
        val latestStart = sorted.last().start
        val next = latestStart.plusDays(predictedDays.toLong())
        return Prediction(
            estimatedStart = next,
            windowStart = maxOf(latestStart.plusDays(1), next.minusDays(halfWidth.toLong())),
            windowEnd = next.plusDays(halfWidth.toLong()),
            cycleDays = predictedDays,
            estimatedDuration = duration(sorted),
            sampleCount = minOf(12, cycles.size),
            totalCycleCount = cycles.size,
            meanAbsoluteError = backtests.takeIf { it.isNotEmpty() }?.map { it.absoluteError }?.average(),
            recentMeanAbsoluteError = backtests.takeLast(12).takeIf { it.isNotEmpty() }
                ?.map { it.absoluteError }?.average(),
            backtests = backtests,
        )
    }
}
