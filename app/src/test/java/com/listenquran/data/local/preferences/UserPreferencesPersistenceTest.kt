package com.listenquran.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesPersistenceTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testScope = CoroutineScope(Dispatchers.IO + Job())

    private val WBW_KEY = booleanPreferencesKey("word_by_word_enabled")
    private val TRANSLATION_KEY = booleanPreferencesKey("translation_enabled")

    private fun createTestDataStore() = PreferenceDataStoreFactory.create(
        scope = testScope,
        produceFile = { tmpFolder.newFile("test_preferences.preferences_pb") }
    )

    @Test
    fun `default values for WBW and translation are false`() = runBlocking {
        val dataStore = createTestDataStore()

        val wbw = dataStore.data.map { it[WBW_KEY] ?: false }.first()
        val trans = dataStore.data.map { it[TRANSLATION_KEY] ?: false }.first()

        assertFalse(wbw)
        assertFalse(trans)
    }

    @Test
    fun `persists all four toggle combinations independently`() = runBlocking {
        val dataStore = createTestDataStore()

        // 1. WBW = ON, Translation = OFF
        dataStore.edit { prefs ->
            prefs[WBW_KEY] = true
            prefs[TRANSLATION_KEY] = false
        }
        assertTrue(dataStore.data.map { it[WBW_KEY] ?: false }.first())
        assertFalse(dataStore.data.map { it[TRANSLATION_KEY] ?: false }.first())

        // 2. WBW = OFF, Translation = ON
        dataStore.edit { prefs ->
            prefs[WBW_KEY] = false
            prefs[TRANSLATION_KEY] = true
        }
        assertFalse(dataStore.data.map { it[WBW_KEY] ?: false }.first())
        assertTrue(dataStore.data.map { it[TRANSLATION_KEY] ?: false }.first())

        // 3. WBW = ON, Translation = ON
        dataStore.edit { prefs ->
            prefs[WBW_KEY] = true
            prefs[TRANSLATION_KEY] = true
        }
        assertTrue(dataStore.data.map { it[WBW_KEY] ?: false }.first())
        assertTrue(dataStore.data.map { it[TRANSLATION_KEY] ?: false }.first())

        // 4. WBW = OFF, Translation = OFF
        dataStore.edit { prefs ->
            prefs[WBW_KEY] = false
            prefs[TRANSLATION_KEY] = false
        }
        assertFalse(dataStore.data.map { it[WBW_KEY] ?: false }.first())
        assertFalse(dataStore.data.map { it[TRANSLATION_KEY] ?: false }.first())
    }
}
