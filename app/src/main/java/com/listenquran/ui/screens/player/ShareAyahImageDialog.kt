package com.listenquran.ui.screens.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.listenquran.domain.model.Ayah
import com.listenquran.ui.theme.AmiriQuranFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

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
    var isSharing by remember { mutableStateOf(false) }

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
                // Rendered Preview Card captured into graphicsLayer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
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
                enabled = !isSharing,
                onClick = {
                    isSharing = true
                    coroutineScope.launch {
                        try {
                            val imageBitmap = graphicsLayer.toImageBitmap()
                            val androidBitmap = imageBitmap.asAndroidBitmap()
                            val uri = withContext(Dispatchers.IO) {
                                saveBitmapToFile(context, androidBitmap, ayah)
                            }
                            if (uri != null) {
                                shareImageUri(context, uri, surahName, ayah)
                            } else {
                                Toast.makeText(context, "Failed to generate image", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ShareAyah", "Error generating image", e)
                            shareAyahFallback(context, surahName, ayah)
                        } finally {
                            isSharing = false
                            onDismiss()
                        }
                    }
                }
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Image")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun saveBitmapToFile(context: Context, bitmap: Bitmap, ayah: Ayah): Uri? {
    return try {
        val cacheFolder = File(context.cacheDir, "shared_ayahs")
        if (!cacheFolder.exists()) {
            cacheFolder.mkdirs()
        }
        val file = File(cacheFolder, "ayah_${ayah.surahNumber}_${ayah.ayahNumber}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        android.util.Log.e("ShareAyah", "Error saving bitmap", e)
        null
    }
}

private fun shareImageUri(context: Context, uri: Uri, surahName: String, ayah: Ayah) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Surah $surahName (${ayah.verseKey}) • Listen Quran\n\n${ayah.textUthmani}\n\n\"${ayah.translation}\"")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Ayah Image"))
}

private fun shareAyahFallback(context: Context, surahName: String, ayah: Ayah) {
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
