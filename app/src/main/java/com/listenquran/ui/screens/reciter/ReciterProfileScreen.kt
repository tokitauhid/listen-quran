package com.listenquran.ui.screens.reciter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.listenquran.domain.model.Reciter
import com.listenquran.util.ReciterImages
import com.listenquran.domain.model.Surah
import com.listenquran.ui.screens.home.SurahListItem
import com.listenquran.ui.theme.AmiriQuranFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterProfileScreen(
    reciter: Reciter,
    surahs: List<Surah>,
    onBack: () -> Unit,
    onPlaySurah: (Surah, Reciter) -> Unit,
    onPlayAll: (Reciter) -> Unit,
    onToggleFavorite: (Reciter) -> Unit,
    onStartRadio: ((Reciter) -> Unit)? = null,
    onSetDefaultReciter: ((Reciter) -> Unit)? = null,
    defaultReciterId: Int = 7,
    allReciters: List<Reciter> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val variants = remember(reciter.englishName, reciter.name, allReciters) {
        val normAr = reciter.name.replace(" ", "")
        val normEn = reciter.englishName.replace(" ", "").replace("-", "").lowercase()
        val matches = allReciters.filter {
            (normAr.isNotBlank() && it.name.replace(" ", "") == normAr) ||
            it.englishName.replace(" ", "").replace("-", "").equals(normEn, ignoreCase = true)
        }
        if (matches.isNotEmpty()) matches else listOf(reciter)
    }

    var currentReciter by remember(reciter.id) {
        mutableStateOf(reciter)
    }

    LaunchedEffect(reciter.id, variants) {
        currentReciter = variants.find { it.id == reciter.id } ?: variants.firstOrNull() ?: reciter
    }

    var isFavorite by remember(currentReciter.id, currentReciter.isFavorite) {
        mutableStateOf(currentReciter.isFavorite)
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentReciter.englishName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isFavorite = !isFavorite
                            onToggleFavorite(currentReciter)
                        }
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from Favorites" else "Favorite Reciter",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reciter Hero Header
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF005237), Color(0xFF1D211F))
                                )
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val imageUrl = remember(currentReciter) {
                            ReciterImages.getPhotoUrl(currentReciter.id, currentReciter.englishName, currentReciter.photoUrl)
                        }
                        var isImageError by remember(imageUrl) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUrl != null && !isImageError) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = currentReciter.englishName,
                                    contentScale = ContentScale.Crop,
                                    onError = { isImageError = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (imageUrl == null || isImageError) {
                                Text(
                                    text = currentReciter.englishName.take(1),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentReciter.englishName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = currentReciter.name,
                            fontFamily = AmiriQuranFont,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Style Switcher Chips (Murattal, Mujawwad, Muallim)
                        if (variants.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                variants.forEach { variant ->
                                    val isSelected = currentReciter.id == variant.id
                                    val styleLabel = variant.style ?: "Murattal"
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            currentReciter = variant
                                            isFavorite = variant.isFavorite
                                        },
                                        label = {
                                            Text(
                                                text = styleLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${currentReciter.qiraah} • ${currentReciter.style ?: "Murattal"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Action Buttons: Play All & Reciter Radio
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onPlayAll(currentReciter) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All")
                            }

                            FilledTonalButton(
                                onClick = { onStartRadio?.invoke(currentReciter) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Radio, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Radio")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val isDefault = currentReciter.id == defaultReciterId
                        if (isDefault) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Default Reciter for App",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSetDefaultReciter?.invoke(currentReciter) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Set as Default Reciter",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Section Header: Available Surahs
            item {
                Text(
                    text = "Surahs (${surahs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Surahs list
            items(surahs, key = { it.id }) { surah ->
                SurahListItem(
                    surah = surah,
                    onClick = { onPlaySurah(surah, currentReciter) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
