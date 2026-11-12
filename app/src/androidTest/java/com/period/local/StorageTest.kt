package com.period.local

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.period.local.data.*
import com.period.local.domain.Period
import java.time.LocalDate
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private lateinit var database: PeriodDatabase
    private lateinit var repository: PeriodRepository
    private val today = LocalDate.of(2026, 11, 12)
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val seed get() = BackupCodec.encode(SyntheticData.records(today))

    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(context, PeriodDatabase::class.java).build()
        repository = PeriodRepository(database)
    }
    @After fun close() { database.close() }

    @Test fun publicStartupIsEmptyAndPreservesImportedRecords() = runBlocking {
        suspend fun startModel() {
            val model = withContext(Dispatchers.Main) {
                PeriodViewModel(ApplicationProvider.getApplicationContext(), repository)
            }
            try {
                withTimeout(10_000) { model.busy.first { !it } }
                assertNull(model.startupError.value)
            } finally { withContext(Dispatchers.Main) { model.viewModelScope.cancel() } }
        }
        startModel()
        assertTrue(repository.all().isEmpty())
        val incoming = BackupCodec.decode(seed, today)
        repository.import(repository.preview(incoming), emptySet(), today)
        startModel()
        assertEquals(incoming, repository.all())
    }

    @Test fun seedIsAtomicAndIdempotentUnderConcurrentStartup() = runBlocking {
        coroutineScope { repeat(4) { launch { repository.initialize(seed, today) } } }
        assertEquals(16, repository.all().size)
        assertEquals(BackupCodec.decode(seed, today), repository.all())
        assertNull(repository.all().last().end)
    }

    @Test fun quickActionsValidateTransitionsAndConcurrentTaps() = runBlocking {
        assertTrue(runCatching { repository.endToday(today) }.isFailure)
        val attempts = coroutineScope {
            List(4) { async { runCatching { repository.startToday(today) }.isSuccess } }.awaitAll()
        }
        assertEquals(1, attempts.count { it })
        val started = repository.all().single()
        assertEquals(today, started.start)
        assertNull(started.end)
        assertTrue(runCatching { repository.startToday(today.plusDays(1)) }.isFailure)
        assertTrue(runCatching { repository.endToday(today.minusDays(1)) }.isFailure)
        assertEquals(started, repository.all().single())
        repository.endToday(today)
        val ended = repository.all().single()
        assertEquals(today, ended.end)
        assertTrue(runCatching { repository.endToday(today.plusDays(1)) }.isFailure)
        assertTrue(runCatching { repository.startToday(today) }.isFailure)
        assertEquals(ended, repository.all().single())
        repository.startToday(today.plusDays(1))
        assertEquals(2, repository.all().size)
        assertEquals(today.plusDays(1), repository.all().last().start)
    }

    @Test fun exportRestoreAndDuplicateImportAreLossless() = runBlocking {
        repository.initialize(seed, today)
        val expected = repository.all()
        assertEquals(expected, BackupCodec.decode(BackupCodec.encode(expected), today))
        val preview = repository.preview(BackupCodec.decode(seed, today))
        assertEquals(16, preview.duplicateCount)
        assertEquals(0, preview.additions.size)
        repository.import(preview, emptySet(), today)
        assertEquals(expected, repository.all())
        val restoredDatabase = Room.inMemoryDatabaseBuilder(context, PeriodDatabase::class.java).build()
        try {
            val restoredRepository = PeriodRepository(restoredDatabase)
            val restoredPreview = restoredRepository.preview(BackupCodec.decode(BackupCodec.encode(expected), today))
            assertEquals(16, restoredPreview.additions.size)
            restoredRepository.import(restoredPreview, emptySet(), today)
            restoredRepository.initialize(seed, today)
            assertEquals(expected, restoredRepository.all())
        } finally { restoredDatabase.close() }
    }

    @Test fun conflictCanKeepExistingOrApplySelectedImport() = runBlocking {
        repository.initialize(seed, today)
        val old = repository.all().last()
        val updated = old.copy(end = today.minusDays(2))
        var preview = repository.preview(listOf(updated))
        assertEquals(1, preview.conflicts.size)
        repository.import(preview, emptySet(), today)
        assertEquals(old, repository.all().last())
        preview = repository.preview(listOf(updated))
        repository.import(preview, setOf(updated.id), today)
        assertEquals(updated, repository.all().last())
    }

    @Test fun staleEditAndStaleImportAreRejected() = runBlocking {
        repository.initialize(seed, today)
        val old = repository.all().last()
        val preview = repository.preview(listOf(old.copy(end = today)))
        val updated = old.copy(end = today.minusDays(1))
        repository.save(updated, old, today)
        assertTrue(runCatching { repository.save(old, old, today) }.isFailure)
        assertTrue(runCatching { repository.import(preview, setOf(old.id), today) }.isFailure)
        assertEquals(updated, repository.all().last())
    }

    @Test fun failedMergedValidationLeavesAllRecordsUnchanged() = runBlocking {
        repository.initialize(seed, today)
        val before = repository.all()
        val preview = repository.preview(listOf(Period.create(before.first().start.plusDays(1))))
        assertTrue(runCatching { repository.import(preview, emptySet(), today) }.isFailure)
        assertEquals(before, repository.all())
    }

    @Test fun midTransactionFailureRollsBack() = runBlocking {
        val result = runCatching {
            database.withTransaction {
                database.periods().insert(listOf(PeriodEntity.from(Period.create(today))))
                throw IllegalStateException("simulated interruption")
            }
        }
        assertTrue(result.isFailure)
        assertTrue(repository.all().isEmpty())
    }

    @Test fun concurrentSavesCannotDuplicateStartDates() = runBlocking {
        val results = coroutineScope {
            List(2) { async { runCatching { repository.save(Period.create(today), null, today) } } }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, repository.all().size)
    }

    @Test fun malformedAndUnsupportedBackupsDoNotMutateDatabase() = runBlocking {
        repository.initialize(seed, today)
        val before = repository.all()
        listOf("{", "{\"schemaVersion\":2,\"records\":[]}", "[] trailing", "[{\"start_date\":\"bad\"}]").forEach {
            assertTrue(runCatching { BackupCodec.decode(it, today) }.isFailure)
        }
        assertEquals(before, repository.all())
    }

    @Test fun persistedDatabaseReopensWithSameData() = runBlocking {
        val name = "period-test-${java.util.UUID.randomUUID()}.db"
        val first = Room.databaseBuilder(context, PeriodDatabase::class.java, name).build()
        try {
            PeriodRepository(first).initialize(seed, today)
        } finally { first.close() }
        val second = Room.databaseBuilder(context, PeriodDatabase::class.java, name).build()
        try {
            val repo = PeriodRepository(second)
            repo.initialize(seed, today)
            assertEquals(16, repo.all().size)
            assertEquals(BackupCodec.decode(seed, today), repo.all())
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }
}
