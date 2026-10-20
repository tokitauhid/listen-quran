package com.listenquran

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.listenquran.domain.model.RepetitionPreset
import com.listenquran.domain.model.Surah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepetitionEditorUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testSurah = Surah(
        id = 1,
        nameSimple = "Al-Fatihah",
        nameComplex = "Al-Fatihah",
        nameArabic = "الفاتحة",
        revelationPlace = "makkah",
        revelationOrder = 5,
        versesCount = 7,
        pages = listOf(1, 1),
        translatedName = "The Opener"
    )

    @Test
    fun testRepetitionEditorTypeEraseAndSave() {
        var savedPreset: RepetitionPreset? = null

        composeTestRule.setContent {
            MaterialTheme {
                com.listenquran.ui.screens.library.RepetitionEditorContent(
                    initialPreset = null,
                    surahs = listOf(testSurah),
                    onSave = { savedPreset = it }
                )
            }
        }

        // Verify initial From Ayah is present
        val fromField = composeTestRule.onNodeWithText("From Ayah")
        fromField.assertExists()

        // Erase From Ayah to empty using the Clear action button
        composeTestRule.onNodeWithContentDescription("Clear start ayah").performClick()

        // Verify Save button is disabled when From Ayah is empty
        composeTestRule.onNodeWithText("Save Preset").assertIsNotEnabled()

        // Type a new start ayah: "2"
        fromField.performTextInput("2")

        // Erase To Ayah using Clear action button
        composeTestRule.onNodeWithContentDescription("Clear to ayah").performClick()

        // Type a new end ayah: "5"
        val toField = composeTestRule.onNodeWithText("To Ayah")
        toField.performTextInput("5")

        // Verify Save button is enabled
        composeTestRule.onNodeWithText("Save Preset").assertIsEnabled()

        // Click Save Preset
        composeTestRule.onNodeWithText("Save Preset").performClick()

        // Assert saved preset values
        assertNotNull(savedPreset)
        assertEquals(2, savedPreset?.startAyah)
        assertEquals(5, savedPreset?.endAyah)
        assertEquals(1, savedPreset?.surahNumber)

        // Reopen editor with savedPreset
        var reopenedSavedPreset: RepetitionPreset? = null
        composeTestRule.setContent {
            MaterialTheme {
                com.listenquran.ui.screens.library.RepetitionEditorContent(
                    initialPreset = savedPreset,
                    surahs = listOf(testSurah),
                    onSave = { reopenedSavedPreset = it }
                )
            }
        }

        // Verify reopened fields show "2" and "5"
        composeTestRule.onNodeWithText("2").assertExists()
        composeTestRule.onNodeWithText("5").assertExists()
    }
}
