package com.period.local.data

import androidx.room.withTransaction
import com.period.local.domain.Period
import com.period.local.domain.PeriodValidation
import java.time.LocalDate
import kotlinx.coroutines.flow.map

data class ImportConflict(val incoming: Period, val existing: Period)
data class ImportPreview(
    val incoming: List<Period>,
    val baseline: List<Period>,
    val additions: List<Period>,
    val duplicateCount: Int,
    val conflicts: List<ImportConflict>,
)

class PeriodRepository(private val database: PeriodDatabase) {
    private val dao = database.periods()
    val records = dao.observe().map { rows -> rows.map { it.toPeriod() } }

    suspend fun all(): List<Period> = dao.all().map { it.toPeriod() }

    suspend fun initialize(seed: String, today: LocalDate) {
        database.withTransaction {
            if (dao.metadata("seed_v1") != null) return@withTransaction
            if (dao.all().isEmpty()) dao.insert(BackupCodec.decode(seed, today).map(PeriodEntity::from))
            dao.putMetadata(Metadata("seed_v1", "complete"))
        }
    }

    suspend fun save(record: Period, expected: Period?, today: LocalDate) {
        database.withTransaction {
            val current = all()
            val stored = current.find { it.id == record.id }
            require(stored == expected) { "记录已发生变化，请重新打开后修改" }
            PeriodValidation.validate(current.filterNot { it.id == record.id } + record, today)
            if (stored == null) dao.insert(listOf(PeriodEntity.from(record)))
            else dao.update(PeriodEntity.from(record))
        }
    }

    suspend fun startToday(today: LocalDate) {
        database.withTransaction {
            val current = all()
            val latest = current.lastOrNull()
            require(latest == null || latest.end != null) { "请先在日历补全上次经期的结束日期" }
            require(latest == null || latest.end!! < today) { "今天已有经期记录" }
            val record = Period.create(today)
            PeriodValidation.validate(current + record, today)
            dao.insert(listOf(PeriodEntity.from(record)))
        }
    }

    suspend fun endToday(today: LocalDate) {
        database.withTransaction {
            val current = all()
            val latest = requireNotNull(current.lastOrNull()) { "请先记录经期开始日期" }
            require(latest.end == null) { "这次经期已记录结束日期" }
            val completed = latest.copy(end = today)
            PeriodValidation.validate(current.dropLast(1) + completed, today)
            dao.update(PeriodEntity.from(completed))
        }
    }

    suspend fun preview(incoming: List<Period>): ImportPreview = database.withTransaction {
        val current = all()
        val additions = mutableListOf<Period>()
        val conflicts = mutableListOf<ImportConflict>()
        var duplicates = 0
        incoming.forEach { row ->
            val byId = current.find { it.id == row.id }
            val byDate = current.find { it.start == row.start }
            require(byId == null || byDate == null || byId.id == byDate.id) {
                "导入记录的 ID 与开始日期分别匹配不同记录，请修正文件"
            }
            val existing = byId ?: byDate
            when {
                existing == null -> additions.add(row)
                existing.start == row.start && existing.end == row.end -> duplicates++
                else -> conflicts.add(ImportConflict(row, existing))
            }
        }
        require(conflicts.map { it.existing.id }.distinct().size == conflicts.size) {
            "多条导入记录对应同一条已有记录，请检查文件"
        }
        ImportPreview(incoming, current, additions, duplicates, conflicts)
    }

    suspend fun import(preview: ImportPreview, replacements: Set<String>, today: LocalDate) {
        database.withTransaction {
            val current = all()
            require(current == preview.baseline) { "预览后记录已变化，请重新导入文件" }
            val updates = preview.conflicts.filter { it.incoming.id in replacements }
                .map { it.incoming.copy(id = it.existing.id) }
            val merged = current.filterNot { old -> updates.any { it.id == old.id } } + updates + preview.additions
            PeriodValidation.validate(merged, today)
            updates.forEach { dao.update(PeriodEntity.from(it)) }
            dao.insert(preview.additions.map(PeriodEntity::from))
        }
    }
}
