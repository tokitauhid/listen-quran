package com.listenquran.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal providing the real-time measured bottom inset of the persistent bottom controls
 * (MiniPlayer and NavigationBar, if visible).
 *
 * All scrollable screens should apply this value to their `contentPadding`:
 * `contentPadding = PaddingValues(..., bottom = LocalMiniPlayerInset.current + 16.dp)`
 *
 * Non-scrolling screens / empty states can use this inset directly to center or position content safely above
 * the player without being obscured.
 */
val LocalMiniPlayerInset = compositionLocalOf<Dp> { 0.dp }
