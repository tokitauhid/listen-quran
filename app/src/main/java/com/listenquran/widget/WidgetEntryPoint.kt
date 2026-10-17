package com.listenquran.widget

import com.listenquran.data.local.dao.KhatmaDao
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.local.dao.SurahDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun khatmaDao(): KhatmaDao
    fun statsDao(): StatsDao
    fun surahDao(): SurahDao
}
