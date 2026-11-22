package com.listenquran.ui.screens.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

fun categoryIconFor(categoryId: String): ImageVector = when (categoryId) {
    "friday_sunnah" -> Icons.Rounded.WbSunny
    "morning_evening_adhkar" -> Icons.Rounded.Shield
    "before_sleep" -> Icons.Rounded.Bedtime
    "night_prayer" -> Icons.Rounded.NightsStay
    "peace_sakinah" -> Icons.Rounded.Spa
    "ruqyah_healing" -> Icons.Rounded.HealthAndSafety
    "mercy_forgiveness" -> Icons.Rounded.Favorite
    "gratitude_barakah" -> Icons.Rounded.VolunteerActivism
    "quranic_duas" -> Icons.AutoMirrored.Rounded.MenuBook
    "stories_prophets" -> Icons.Rounded.HistoryEdu
    "akhirah_reflection" -> Icons.Rounded.DarkMode
    "browse_juz" -> Icons.Rounded.AutoStories
    "juz_amma" -> Icons.Rounded.Bolt
    "length_revelation" -> Icons.Rounded.Tune
    else -> Icons.Rounded.AutoStories
}
