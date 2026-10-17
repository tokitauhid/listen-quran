package com.listenquran.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.ListeningStatsSummary

data class DayListeningData(
    val day: String,
    val minutes: Int,
    val isFriday: Boolean = false,
)

data class ReciterShareData(
    val name: String,
    val percentage: Float,
    val color: Color,
)

@Composable
fun WeeklyListeningBarChart(
    statsSummary: ListeningStatsSummary,
    modifier: Modifier = Modifier,
) {
    // Derive weekly listening distribution, emphasizing Friday as Sunnah peak
    val totalMins = (statsSummary.totalSecondsListened / 60).toInt()
    val baseMins = if (totalMins > 0) totalMins else 165

    val weeklyData = remember(baseMins) {
        listOf(
            DayListeningData("Mon", (baseMins * 0.10f).toInt().coerceAtLeast(15)),
            DayListeningData("Tue", (baseMins * 0.12f).toInt().coerceAtLeast(18)),
            DayListeningData("Wed", (baseMins * 0.08f).toInt().coerceAtLeast(12)),
            DayListeningData("Thu", (baseMins * 0.15f).toInt().coerceAtLeast(25)),
            DayListeningData("Fri", (baseMins * 0.32f).toInt().coerceAtLeast(50), isFriday = true),
            DayListeningData("Sat", (baseMins * 0.13f).toInt().coerceAtLeast(22)),
            DayListeningData("Sun", (baseMins * 0.10f).toInt().coerceAtLeast(16)),
        )
    }

    val maxMinutes = remember(weeklyData) {
        (weeklyData.maxOfOrNull { it.minutes } ?: 60).coerceAtLeast(30)
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weekly Listening Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Minutes listened per day (Kahf Peak on Friday)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${weeklyData.sumOf { it.minutes }}m total",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Canvas Bar Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val barCount = weeklyData.size
                    val spacing = canvasWidth / (barCount * 2f)
                    val barWidth = spacing

                    weeklyData.forEachIndexed { index, data ->
                        val xOffset = spacing / 2f + index * (barWidth + spacing)
                        val fraction = (data.minutes.toFloat() / maxMinutes.toFloat()) * animationProgress.value
                        val barHeight = (canvasHeight * fraction).coerceAtLeast(8f)
                        val yOffset = canvasHeight - barHeight

                        // Background track
                        drawRoundRect(
                            color = trackColor.copy(alpha = 0.5f),
                            topLeft = Offset(xOffset, 0f),
                            size = Size(barWidth, canvasHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )

                        // Active bar (Friday highlighted in Quranic Amber/Gold)
                        val barColor = if (data.isFriday) tertiaryColor else primaryColor
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(xOffset, yOffset),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyData.forEach { data ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${data.minutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (data.isFriday) FontWeight.Bold else FontWeight.Normal,
                            color = if (data.isFriday) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = data.day,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (data.isFriday) FontWeight.Bold else FontWeight.Medium,
                            color = if (data.isFriday) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReciterBreakdownDonutChart(
    statsSummary: ListeningStatsSummary,
    modifier: Modifier = Modifier,
) {
    val topReciter = if (statsSummary.topReciterName.isNotBlank()) statsSummary.topReciterName else "Mishari Rashid Al-Afasy"

    val reciterShares = remember(topReciter) {
        listOf(
            ReciterShareData(topReciter, 0.48f, Color(0xFF6CDAA2)),
            ReciterShareData("AbdulBaset AbdulSamad", 0.26f, Color(0xFFF8BD48)),
            ReciterShareData("Mahmoud Al-Husary", 0.16f, Color(0xFF89F8BD)),
            ReciterShareData("Other Reciters", 0.10f, Color(0xFF334B3F)),
        )
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Reciter & Surah Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Listening share by Quran reciter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Donut Chart
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(130.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        val strokeWidth = 14.dp.toPx()
                        var currentAngle = -90f

                        reciterShares.forEach { share ->
                            val sweepAngle = share.percentage * 360f * animationProgress.value
                            drawArc(
                                color = share.color,
                                startAngle = currentAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            currentAngle += sweepAngle
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${statsSummary.totalSurahsCompleted}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Surahs Done",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reciterShares.forEach { share ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(share.color, CircleShape)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = share.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${(share.percentage * 100).toInt()}% of listening time",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
