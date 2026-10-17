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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ListenQuranSmallWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        var surahTitle = "Surah Al-Fatihah"
        var reciterSubtitle = "Mishari Al-Afasy"

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
                        reciterSubtitle = "${surah.nameArabic} • ${surah.versesCount} Ayahs"
                    }
                }
            } catch (_: Exception) {
                // Fallback to default Al-Fatihah
            }
        }

        provideContent {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1D211F))
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity(mainIntent))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quran Icon in primary emerald box
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .background(Color(0xFF005237))
                            .cornerRadius(10.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_quran),
                            contentDescription = "Quran",
                            modifier = GlanceModifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(10.dp))

                    // Title & Subtitle
                    Column(
                        modifier = GlanceModifier.defaultWeight()
                    ) {
                        Text(
                            text = surahTitle,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = reciterSubtitle,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF6CDAA2)),
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    // Media Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(36.dp)
                                .background(Color(0xFF6CDAA2))
                                .cornerRadius(18.dp)
                                .clickable(actionRunCallback<TogglePlayActionCallback>())
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_play),
                                contentDescription = "Play/Pause",
                                modifier = GlanceModifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Box(
                            modifier = GlanceModifier
                                .size(32.dp)
                                .background(Color(0xFF282B29))
                                .cornerRadius(16.dp)
                                .clickable(actionRunCallback<SkipNextActionCallback>())
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_skip_next),
                                contentDescription = "Skip Next",
                                modifier = GlanceModifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
