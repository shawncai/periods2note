package com.period.local.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.period.local.domain.Period
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "periods", indices = [Index(value = ["startDay"], unique = true)])
data class PeriodEntity(@PrimaryKey val id: String, val startDay: Long, val endDay: Long?) {
    fun toPeriod() = Period(id, LocalDate.ofEpochDay(startDay), endDay?.let(LocalDate::ofEpochDay))
    companion object {
        fun from(period: Period) = PeriodEntity(period.id, period.start.toEpochDay(), period.end?.toEpochDay())
    }
}

@Entity(tableName = "metadata")
data class Metadata(@PrimaryKey val key: String, val value: String)

@Dao
interface PeriodDao {
    @Query("SELECT * FROM periods ORDER BY startDay")
    fun observe(): Flow<List<PeriodEntity>>
    @Query("SELECT * FROM periods ORDER BY startDay")
    suspend fun all(): List<PeriodEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(records: List<PeriodEntity>)
    @Update
    suspend fun update(record: PeriodEntity)
    @Query("SELECT value FROM metadata WHERE `key` = :key")
    suspend fun metadata(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(metadata: Metadata)
}

@Database(entities = [PeriodEntity::class, Metadata::class], version = 1, exportSchema = true)
abstract class PeriodDatabase : RoomDatabase() {
    abstract fun periods(): PeriodDao
}
