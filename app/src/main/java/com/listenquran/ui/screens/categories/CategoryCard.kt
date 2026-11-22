package com.listenquran.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.BrowseCategory
import com.listenquran.domain.model.CategoryKind
import com.listenquran.domain.model.CategoryTone
import com.listenquran.domain.model.FeaturedCategorySpotlight
import com.listenquran.ui.theme.AmiriQuranFont

internal data class CategoryTonePalette(
    val badgeContainer: Color,
    val badgeContent: Color,
    val cardWash: Color,
)

internal fun resolveCategoryTonePalette(
    tone: CategoryTone,
    colorScheme: ColorScheme,
): CategoryTonePalette = when (tone) {
    CategoryTone.PRIMARY -> CategoryTonePalette(
        badgeContainer = colorScheme.primaryContainer,
        badgeContent = colorScheme.onPrimaryContainer,
        cardWash = colorScheme.primaryContainer.copy(alpha = 0.16f),
    )
    CategoryTone.SECONDARY -> CategoryTonePalette(
        badgeContainer = colorScheme.secondaryContainer,
        badgeContent = colorScheme.onSecondaryContainer,
        cardWash = colorScheme.secondaryContainer.copy(alpha = 0.18f),
    )
    CategoryTone.TERTIARY -> CategoryTonePalette(
        badgeContainer = colorScheme.tertiaryContainer,
        badgeContent = colorScheme.onTertiaryContainer,
        cardWash = colorScheme.tertiaryContainer.copy(alpha = 0.20f),
    )
    CategoryTone.SURFACE_HIGH -> CategoryTonePalette(
        badgeContainer = colorScheme.surfaceContainerHighest,
        badgeContent = colorScheme.onSurface,
        cardWash = colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun FeaturedCategoryBanner(
    spotlight: FeaturedCategorySpotlight,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = spotlight.category
    val colorScheme = MaterialTheme.colorScheme
    val tonePalette = resolveCategoryTonePalette(category.tone, colorScheme)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${spotlight.badgeLabel}: ${category.title}. ${spotlight.contextSubtitle}. ${category.itemCountLabel}"
            },
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(tonePalette.cardWash, colorScheme.surfaceContainer)
                    )
                )
        ) {
            Text(
                text = category.arabicLabel,
                fontFamily = AmiriQuranFont,
                fontSize = 24.sp,
                lineHeight = 52.sp,
                color = colorScheme.onSurface.copy(alpha = 0.08f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 2.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tonePalette.badgeContainer,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIconFor(category.id),
                            contentDescription = null,
                            tint = tonePalette.badgeContent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tonePalette.badgeContainer,
                        ) {
                            Text(
                                text = spotlight.badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tonePalette.badgeContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = category.itemCountLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = spotlight.contextSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalIconButton(
                    onClick = onQuickPlay,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = tonePalette.badgeContainer,
                        contentColor = tonePalette.badgeContent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${category.title}",
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: BrowseCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onQuickPlay: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tonePalette = resolveCategoryTonePalette(category.tone, colorScheme)

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 122.dp)
            .semantics {
                contentDescription = "${category.title}, ${category.description}, ${category.itemCountLabel}"
            },
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 122.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(tonePalette.cardWash, colorScheme.surfaceContainer)
                    )
                )
        ) {
            Text(
                text = category.arabicLabel,
                fontFamily = AmiriQuranFont,
                fontSize = 20.sp,
                lineHeight = 44.sp,
                color = colorScheme.onSurface.copy(alpha = 0.08f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 2.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tonePalette.badgeContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = categoryIconFor(category.id),
                                contentDescription = null,
                                tint = tonePalette.badgeContent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (category.kind == CategoryKind.CURATED_COLLECTION && onQuickPlay != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClickLabel = "Play ${category.title}",
                                    role = Role.Button,
                                    onClick = onQuickPlay,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = tonePalette.badgeContainer.copy(alpha = 0.9f),
                                modifier = Modifier.size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play ${category.title}",
                                        tint = tonePalette.badgeContent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = category.itemCountLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
