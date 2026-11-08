package com.listenquran.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Listen Quran — Material Design 3 Shape Scale & Tokens
 *
 * Implements the official M3 shape scale from 4dp to 28dp/32dp,
 * with expressive extensions for Quranic medallions, pill chips,
 * and hero containers.
 */
val QuranShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Expressive Shape Tokens
val PillShape = CircleShape
val MedallionShape = RoundedCornerShape(10.dp)
val HeroCardShape = RoundedCornerShape(24.dp)
val MiniPlayerShape = RoundedCornerShape(16.dp)
val BottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

