package com.listenquran.ui.screens.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.listenquran.domain.model.Ayah
import com.listenquran.ui.theme.AmiriQuranFont
import java.io.File
import java.io.FileOutputStream

data class ShareBackgroundTheme(
    val name: String,
    val primaryColor: Color,
    val gradient: Brush,
    val textColor: Color = Color(0xFFE1E3DF),
)

@Composable
fun ShareAyahImageDialog(
    ayah: Ayah,
    surahName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val backgrounds = listOf(
        ShareBackgroundTheme(
            "Islamic Emerald",
            Color(0xFF005237),
            Brush.verticalGradient(listOf(Color(0xFF003822), Color(0xFF005237), Color(0xFF111413)))
        ),
        ShareBackgroundTheme(
            "Night Sky",
            Color(0xFF111413),
            Brush.verticalGradient(listOf(Color(0xFF0C0F0E), Color(0xFF191C1B), Color(0xFF282B29)))
        ),
        ShareBackgroundTheme(
            "Quranic Gold",
            Color(0xFF5F4100),
            Brush.verticalGradient(listOf(Color(0xFF422D00), Color(0xFF5F4100), Color(0xFF1D211F)))
        ),
        ShareBackgroundTheme(
            "Deep Teal",
            Color(0xFF004D40),
            Brush.verticalGradient(listOf(Color(0xFF00251A), Color(0xFF004D40), Color(0xFF00796B)))
        ),
    )

    var selectedBg by remember { mutableStateOf(backgrounds.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Share Ayah as Image",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Preview Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(selectedBg.gradient)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF8BD48).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF8BD48))
                        ) {
                            Text(
                                text = "Surah $surahName • Ayah ${ayah.verseKey}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF8BD48),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = ayah.textUthmani,
                            fontFamily = AmiriQuranFont,
                            fontSize = 22.sp,
                            lineHeight = 36.sp,
                            color = selectedBg.textColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "\"${ayah.translation}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = selectedBg.textColor.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Listen Quran",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = Color(0xFF6CDAA2),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Theme:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Background Theme Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    backgrounds.forEach { bg ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(bg.gradient)
                                .border(
                                    width = if (selectedBg == bg) 2.5.dp else 1.dp,
                                    color = if (selectedBg == bg) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedBg = bg },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedBg == bg) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    shareAyahCardText(context, surahName, ayah)
                    onDismiss()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun shareAyahCardText(context: Context, surahName: String, ayah: Ayah) {
    val text = """
        📖 Surah $surahName (${ayah.verseKey})
        
        ${ayah.textUthmani}
        
        "${ayah.translation}"
        
        Shared via Listen Quran
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Ayah"))
}
