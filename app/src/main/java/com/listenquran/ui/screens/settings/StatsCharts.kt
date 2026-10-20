package com.listenquran.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.DailyListeningActivity
import com.listenquran.domain.model.ListeningStatsSummary
import com.listenquran.domain.model.ReciterListeningShare

data class ReciterColorShare(
    val name: String,
    val percentage: Float,
    val seconds: Long,
    val color: Color,
)

@Composable
fun WeeklyListeningBarChart(
    statsSummary: ListeningStatsSummary,
    modifier: Modifier = Modifier,
) {
    val weeklyData = statsSummary.weeklyActivity
    val hasData = weeklyData.any { it.minutes > 0 }
    val totalWeeklyMinutes = weeklyData.sumOf { it.minutes }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    val maxMinutes = remember(weeklyData) {
        (weeklyData.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)
    }

    val chartDescription = remember(weeklyData, totalWeeklyMinutes) {
        if (!hasData) {
            "Weekly listening activity: No listening recorded yet this week."
        } else {
            val breakdown = weeklyData.joinToString(", ") { "${it.dayLabel}: ${it.minutes}m" }
            "Weekly listening activity: $totalWeeklyMinutes minutes total. $breakdown"
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weekly Listening Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Daily minutes over the past 7 days (Kahf on Friday)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${totalWeeklyMinutes}m total",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!hasData) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Listen to see your weekly stats",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyData.forEach { data ->
                        val targetBarHeightRatio = (data.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                        val animatedRatio = targetBarHeightRatio * animationProgress.value

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (data.minutes > 0) "${data.minutes}m" else "0m",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (data.isFriday) tertiaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (data.isFriday) FontWeight.Bold else FontWeight.Normal
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(90.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                // Background Track
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRoundRect(
                                        color = trackColor,
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                        size = size
                                    )
                                }

                                // Active Fill Bar
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val barHeight = (size.height * animatedRatio).coerceAtLeast(if (data.minutes > 0) 4.dp.toPx() else 0f)
                                    drawRoundRect(
                                        color = if (data.isFriday) tertiaryColor else primaryColor,
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                        topLeft = Offset(0f, size.height - barHeight),
                                        size = Size(size.width, barHeight)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = data.dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (data.isFriday) FontWeight.Bold else FontWeight.Normal,
                                color = if (data.isFriday) tertiaryColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
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
    val shares = statsSummary.reciterShares
    val hasData = shares.isNotEmpty() && statsSummary.totalSecondsListened > 0L

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.outlineVariant
    )

    val colorShares = remember(shares, palette) {
        shares.mapIndexed { index, share ->
            val color = palette[index % palette.size]
            ReciterColorShare(
                name = share.reciterName,
                percentage = share.percentage,
                seconds = share.seconds,
                color = color
            )
        }
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    val donutDescription = remember(colorShares) {
        if (!hasData) {
            "Reciter breakdown: No listening data yet."
        } else {
            val breakdown = colorShares.joinToString(", ") { "${it.name}: ${(it.percentage * 100).toInt()}%" }
            "Reciter breakdown: $breakdown"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = donutDescription },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Reciter Breakdown",
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

            if (!hasData) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PieChart,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Listen to reciters to see breakdown",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
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

                            colorShares.forEach { share ->
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

                        // Donut Center Stats
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${statsSummary.totalSurahsCompleted}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Surahs Done",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Legend & Percentage Breakdown
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        colorShares.forEach { share ->
                            val pct = (share.percentage * 100).toInt()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
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
                                        text = "$pct% of listening time (${share.seconds / 60}m)",
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
}
