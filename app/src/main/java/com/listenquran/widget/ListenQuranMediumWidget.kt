package com.listenquran.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.listenquran.MainActivity
import com.listenquran.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ListenQuranMediumWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        var surahTitle = "Surah Al-Fatihah"
        var surahArabic = "الفاتحة"
        var reciterName = "Mishari Rashid Al-Afasy"
        var currentPage = 1
        val totalPages = 604
        var currentJuz = 1
        var khatmaPercentage = 0

        withContext(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                )
                val recentSurahNumbers = entryPoint.statsDao().getRecentSurahNumbers(1).first()
                if (recentSurahNumbers.isNotEmpty()) {
                    val surah = entryPoint.surahDao().getSurahById(recentSurahNumbers.first())
                    if (surah != null) {
                        surahTitle = "${surah.id}. ${surah.nameSimple}"
                        surahArabic = surah.nameArabic
                    }
                }

                val khatma = entryPoint.khatmaDao().getKhatmaProgress().first()
                if (khatma != null) {
                    currentPage = khatma.currentPage
                    currentJuz = khatma.currentJuz
                    khatmaPercentage = ((currentPage.toFloat() / totalPages.toFloat()) * 100).toInt().coerceIn(0, 100)
                }
            } catch (_: Exception) {
                // Fallback defaults
            }
        }

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF191C1B))
                    .cornerRadius(18.dp)
                    .clickable(actionStartActivity(mainIntent))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top Row: Playing Now info & Media Controls
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(44.dp)
                            .background(Color(0xFF005237))
                            .cornerRadius(12.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_quran),
                            contentDescription = "Quran",
                            modifier = GlanceModifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(12.dp))

                    Column(
                        modifier = GlanceModifier.defaultWeight()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = surahTitle,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                text = surahArabic,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF6CDAA2)),
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }
                        Text(
                            text = reciterName,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFCDE8D7)),
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    // Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(40.dp)
                                .background(Color(0xFF6CDAA2))
                                .cornerRadius(20.dp)
                                .clickable(actionRunCallback<TogglePlayActionCallback>())
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_play),
                                contentDescription = "Play/Pause",
                                modifier = GlanceModifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Box(
                            modifier = GlanceModifier
                                .size(34.dp)
                                .background(Color(0xFF282B29))
                                .cornerRadius(17.dp)
                                .clickable(actionRunCallback<SkipNextActionCallback>())
                                .padding(7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_skip_next),
                                contentDescription = "Skip",
                                modifier = GlanceModifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(10.dp))

                // Bottom Section: Khatma Progress Card
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(Color(0xFF282B29))
                        .cornerRadius(12.dp)
                        .padding(10.dp)
                ) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gold Khatma Badge
                            Box(
                                modifier = GlanceModifier
                                    .background(Color(0xFF7D5700))
                                    .cornerRadius(6.dp)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$khatmaPercentage% Khatma",
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFF8BD48)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(8.dp))

                            Text(
                                text = "Page $currentPage of $totalPages • Juz $currentJuz",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFCDE8D7)),
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight()
                            )

                            Text(
                                text = "Continue ➔",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF6CDAA2)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f),
                            color = ColorProvider(Color(0xFF6CDAA2)),
                            backgroundColor = ColorProvider(Color(0xFF1D211F)),
                            modifier = GlanceModifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
            }
        }
    }
}
