package com.listenquran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.listenquran.domain.model.LikedSurah
import com.listenquran.domain.model.Playlist
import com.listenquran.domain.model.PlaylistItem
import com.listenquran.ui.screens.library.LibraryScreen
import com.listenquran.ui.screens.playlist.PlaylistDetailScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLikedEntryUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleLikedSurah1 = LikedSurah(
        id = 1L,
        surahNumber = 1,
        surahName = "Al-Fatihah",
        surahArabic = "الفاتحة",
        reciterId = 7,
        reciterName = "Mishari Rashid Alafasy"
    )

    private val sampleLikedSurah2 = LikedSurah(
        id = 2L,
        surahNumber = 36,
        surahName = "Ya-Sin",
        surahArabic = "يس",
        reciterId = 7,
        reciterName = "Mishari Rashid Alafasy"
    )

    private val likedPlaylist = Playlist(
        id = 1L,
        name = "Liked Surahs",
        isLikedCollection = true
    )

    @Test
    fun testLibraryShowsSingleLikedEntryAndNoInlineTracks() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    playlists = listOf(likedPlaylist),
                    bookmarks = emptyList(),
                    favoriteReciters = emptyList(),
                    surahs = emptyList(),
                    likedSurahs = listOf(sampleLikedSurah1),
                    onPlaylistClick = {},
                    onCreatePlaylist = { _, _ -> },
                    onBookmarkClick = {},
                    onUpdateBookmarkNote = { _, _ -> },
                    onReciterClick = {},
                    onStartHifzLoop = {}
                )
            }
        }

        // 1. Verify exactly one Liked entry card exists
        composeTestRule.onNodeWithContentDescription("Liked, 1 items, opens liked list").assertExists()
        composeTestRule.onNodeWithText("Liked").assertExists()
        composeTestRule.onNodeWithText("1 recitation").assertExists()

        // 2. Verify NO inline track rows exist
        composeTestRule.onNodeWithText("Al-Fatihah").assertDoesNotExist()

        // 3. Verify NO legacy action buttons (Play all / See all for Liked) exist
        composeTestRule.onNodeWithText("Play all").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Play all liked").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("See all liked").assertDoesNotExist()
    }

    @Test
    fun testLikedCountUpdatesDynamicallyOnLikeAndUnlike() {
        val likedState = mutableStateOf<List<LikedSurah>>(emptyList())

        composeTestRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    playlists = listOf(likedPlaylist),
                    bookmarks = emptyList(),
                    favoriteReciters = emptyList(),
                    surahs = emptyList(),
                    likedSurahs = likedState.value,
                    onPlaylistClick = {},
                    onCreatePlaylist = { _, _ -> },
                    onBookmarkClick = {},
                    onUpdateBookmarkNote = { _, _ -> },
                    onReciterClick = {},
                    onStartHifzLoop = {}
                )
            }
        }

        // Empty state: 0 items
        composeTestRule.onNodeWithContentDescription("Liked, 0 items, opens liked list").assertExists()
        composeTestRule.onNodeWithText("0 recitations · Tap to view liked recitations").assertExists()

        // Update state to 1 liked item
        likedState.value = listOf(sampleLikedSurah1)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Liked, 1 items, opens liked list").assertExists()
        composeTestRule.onNodeWithText("1 recitation").assertExists()
        composeTestRule.onNodeWithText("Al-Fatihah").assertDoesNotExist()

        // Update state to 2 liked items
        likedState.value = listOf(sampleLikedSurah1, sampleLikedSurah2)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Liked, 2 items, opens liked list").assertExists()
        composeTestRule.onNodeWithText("2 recitations").assertExists()
        composeTestRule.onNodeWithText("Ya-Sin").assertDoesNotExist()

        // Unlike an item -> drops back to 1
        likedState.value = listOf(sampleLikedSurah2)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Liked, 1 items, opens liked list").assertExists()
        composeTestRule.onNodeWithText("1 recitation").assertExists()
    }

    @Test
    fun testTapLikedEntryOpensLikedPageAndBackReturnsToLibrary() {
        composeTestRule.setContent {
            MaterialTheme {
                var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
                val items = listOf(
                    sampleLikedSurah1.toPlaylistItem(likedPlaylist.id)
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    LibraryScreen(
                        playlists = listOf(likedPlaylist),
                        bookmarks = emptyList(),
                        favoriteReciters = emptyList(),
                        surahs = emptyList(),
                        likedSurahs = listOf(sampleLikedSurah1),
                        onPlaylistClick = { selectedPlaylist = it },
                        onCreatePlaylist = { _, _ -> },
                        onBookmarkClick = {},
                        onUpdateBookmarkNote = { _, _ -> },
                        onReciterClick = {},
                        onStartHifzLoop = {}
                    )

                    if (selectedPlaylist != null) {
                        PlaylistDetailScreen(
                            playlist = selectedPlaylist!!,
                            items = items,
                            onBack = { selectedPlaylist = null },
                            onPlayTrack = {},
                            onPlayAll = {},
                            onDeleteItem = {},
                            onDeletePlaylist = {}
                        )
                    }
                }
            }
        }

        // Library is open: click the Liked entry card
        composeTestRule.onNodeWithContentDescription("Liked, 1 items, opens liked list")
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()

        // Liked page is now open: track row "Al-Fatihah" and "1 Recitations" exist
        composeTestRule.onNodeWithText("Liked Surahs").assertExists()
        composeTestRule.onNodeWithText("Al-Fatihah").assertExists()
        composeTestRule.onNodeWithText("1 Recitations").assertExists()

        // Tap Back button on the Liked page
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        // Verifying we are back on Library: single Liked entry card is visible, track row is gone
        composeTestRule.onNodeWithContentDescription("Liked, 1 items, opens liked list").assertExists()
        composeTestRule.onNodeWithText("Al-Fatihah").assertDoesNotExist()
    }
}
