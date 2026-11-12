package com.period.local

import android.app.Application
import androidx.room.Room
import com.period.local.data.PeriodDatabase
import com.period.local.data.PeriodRepository

class PeriodApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, PeriodDatabase::class.java, "period.db").build()
    }
    val repository by lazy { PeriodRepository(database) }
}
