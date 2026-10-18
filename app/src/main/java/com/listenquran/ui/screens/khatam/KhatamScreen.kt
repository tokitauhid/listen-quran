package com.listenquran.ui.screens.khatam

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listenquran.R
import com.listenquran.data.local.khatam.KhatamEntity
import com.listenquran.data.repository.khatam.ImportMode
import com.listenquran.domain.model.khatam.AyahRangeSet
import com.listenquran.domain.model.khatam.QuranAyahMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatamScreen(
    viewModel: KhatamViewModel,
    onBackClick: () -> Unit,
    onContinueAyah: (surahNumber: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeKhatam by viewModel.activeKhatam.collectAsStateWithLifecycle()
    val rangeSet by viewModel.rangeSet.collectAsStateWithLifecycle()
    val todayAyahsRead by viewModel.todayAyahsRead.collectAsStateWithLifecycle()
    val goalStatus by viewModel.goalStatus.collectAsStateWithLifecycle()
    val allKhatams by viewModel.allKhatams.collectAsStateWithLifecycle()
    val emergencyWarning by viewModel.emergencyWarning.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }
    var selectedJuzForSheet by remember { mutableStateOf<Int?>(null) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showNewKhatamDialog by remember { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }
    var largeRangeConfirmStartEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // SAF Document Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { os ->
                viewModel.exportBackup(os)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                viewModel.importBackup(inputStream, ImportMode.MERGE)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeKhatam?.title ?: "Khatam Tracker",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.semantics { contentDescription = "More backup and goal options" }
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Set Goal") },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showGoalDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Start New Khatam") },
                            leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showNewKhatamDialog = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Back up now (JSON)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportLauncher.launch("listenquran_khatam_backup_$dateStr.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restore Backup (JSON)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 80.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Emergency Warning Banner
            emergencyWarning?.let { warning ->
                item(span = { GridItemSpan(3) }) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Hero Overall Progress Card
            item(span = { GridItemSpan(3) }) {
                HeroProgressCard(
                    rangeSet = rangeSet,
                    onContinue = {
                        val nextAyah = rangeSet.nextUnreadAyah ?: 1
                        val (s, a) = QuranAyahMetadata.toSurahAyah(nextAyah)
                        onContinueAyah(s, a)
                    }
                )
            }

            // Today Progress & Goal Card
            item(span = { GridItemSpan(3) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$todayAyahsRead Ayahs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Read or listened",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showGoalDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Target Goal",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (goalStatus.hasGoal) "${goalStatus.dailyTargetAyahs} / day" else "No target",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (goalStatus.hasGoal) goalStatus.statusText else "Tap to set goal",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (goalStatus.isOnTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Quick Mark Action Chips
            item(span = { GridItemSpan(3) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { showRangeDialog = true },
                        label = { Text("Mark Range") },
                        leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            val next = rangeSet.nextUnreadAyah ?: 1
                            if (next > 1) {
                                largeRangeConfirmStartEnd = 1 to (next - 1)
                            }
                        },
                        label = { Text("I read up to here") },
                        leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            // 30 Juz Header
            item(span = { GridItemSpan(3) }) {
                Text(
                    text = "30 Juz Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // 30 Juz Grid Items
            items(items = (1..30).toList(), key = { it }) { juzNumber ->
                JuzGridCard(
                    juzNumber = juzNumber,
                    rangeSet = rangeSet,
                    onClick = { selectedJuzForSheet = juzNumber }
                )
            }

            // History Section Header
            if (allKhatams.size > 1) {
                item(span = { GridItemSpan(3) }) {
                    Text(
                        text = "Khatam History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                items(items = allKhatams.filter { !it.isActive }, key = { it.id }, span = { GridItemSpan(3) }) { historyKhatam ->
                    HistoryKhatamCard(khatam = historyKhatam)
                }
            }
        }
    }

    // Juz Detail Sheet
    selectedJuzForSheet?.let { juz ->
        JuzDetailBottomSheet(
            juzNumber = juz,
            rangeSet = rangeSet,
            onDismiss = { selectedJuzForSheet = null },
            onMarkJuz = {
                viewModel.markJuz(juz)
                selectedJuzForSheet = null
            },
            onUnmarkJuz = {
                viewModel.unmarkJuz(juz)
                selectedJuzForSheet = null
            }
        )
    }

    // Set Goal Dialog
    if (showGoalDialog) {
        GoalSettingDialog(
            currentDays = activeKhatam?.targetDays ?: 30,
            onDismiss = { showGoalDialog = false },
            onConfirm = { days ->
                viewModel.updateGoal(days)
                showGoalDialog = false
            }
        )
    }

    // Start New Khatam Dialog
    if (showNewKhatamDialog) {
        NewKhatamDialog(
            onDismiss = { showNewKhatamDialog = false },
            onConfirm = { title, days ->
                viewModel.startNewKhatam(title, days)
                showNewKhatamDialog = false
            }
        )
    }

    // Mark Range Dialog
    if (showRangeDialog) {
        MarkRangeDialog(
            onDismiss = { showRangeDialog = false },
            onConfirm = { start, end ->
                showRangeDialog = false
                val count = end - start + 1
                if (count > 200) {
                    largeRangeConfirmStartEnd = start to end
                } else {
                    viewModel.markRange(start, end)
                }
            }
        )
    }

    // Large Range Confirmation Dialog
    largeRangeConfirmStartEnd?.let { (start, end) ->
        val count = end - start + 1
        AlertDialog(
            onDismissRequest = { largeRangeConfirmStartEnd = null },
            title = { Text("Confirm Large Range") },
            text = {
                Text("You are marking $count ayahs (from Ayah $start to $end). Are you sure?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.markRange(start, end)
                        largeRangeConfirmStartEnd = null
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { largeRangeConfirmStartEnd = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HeroProgressCard(
    rangeSet: AyahRangeSet,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                CircularProgressIndicator(
                    progress = { rangeSet.completionPercentage / 100f },
                    strokeWidth = 9.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f%%".format(rangeSet.completionPercentage),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${rangeSet.completedCount} of ${QuranAyahMetadata.TOTAL_AYAHS} Ayahs Complete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val completedJuzCount = (1..30).count { rangeSet.isJuzComplete(it) }
            Text(
                text = "$completedJuzCount of 30 Juz Completed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            val nextAyah = rangeSet.nextUnreadAyah
            if (nextAyah != null) {
                val (surah, ayah) = QuranAyahMetadata.toSurahAyah(nextAyah)
                val surahName = QuranAyahMetadata.surahEnglishNames.getOrElse(surah - 1) { "Surah $surah" }

                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue: $surahName, Ayah $ayah",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Celebration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Mabrook! You have completed the entire Quran! 🎉",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JuzGridCard(
    juzNumber: Int,
    rangeSet: AyahRangeSet,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isComplete = rangeSet.isJuzComplete(juzNumber)
    val completedCount = rangeSet.juzCompletedCount(juzNumber)
    val totalInJuz = QuranAyahMetadata.juzRange(juzNumber).let { it.last - it.first + 1 }
    val progress = (completedCount.toFloat() / totalInJuz.toFloat()).coerceIn(0f, 1f)
    val arabicName = QuranAyahMetadata.juzArabicName(juzNumber)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Juz $juzNumber, $arabicName, ${(progress * 100).toInt()}% completed"
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Juz $juzNumber",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = arabicName,
                style = MaterialTheme.typography.titleMedium.copy(
                    lineHeight = 28.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$completedCount / $totalInJuz",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JuzDetailBottomSheet(
    juzNumber: Int,
    rangeSet: AyahRangeSet,
    onDismiss: () -> Unit,
    onMarkJuz: () -> Unit,
    onUnmarkJuz: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState()
    val isComplete = rangeSet.isJuzComplete(juzNumber)
    val completedCount = rangeSet.juzCompletedCount(juzNumber)
    val juzRange = QuranAyahMetadata.juzRange(juzNumber)
    val totalInJuz = juzRange.last - juzRange.first + 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Juz $juzNumber: ${QuranAyahMetadata.juzArabicName(juzNumber)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ayahs ${juzRange.first} to ${juzRange.last} ($completedCount of $totalInJuz completed)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (!isComplete) {
                Button(
                    onClick = onMarkJuz,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark Entire Juz as Completed")
                }
            } else {
                OutlinedButton(
                    onClick = onUnmarkJuz,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unmark Juz (Undo)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistoryKhatamCard(khatam: KhatamEntity) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = khatam.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val completedText = khatam.completedAt?.let { "Completed: ${sdf.format(Date(it))}" } ?: "In progress"
                Text(
                    text = completedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun GoalSettingDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedDays by remember { mutableIntStateOf(currentDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Khatam Pace Goal") },
        text = {
            Column {
                Text("Select target days to complete the Quran:")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(15, 30, 60, 90).forEach { days ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = { selectedDays = days },
                            label = { Text("$days Days") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDays) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NewKhatamDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, days: Int) -> Unit
) {
    var title by remember { mutableStateOf("My Khatam") }
    var selectedDays by remember { mutableIntStateOf(30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Khatam") },
        text = {
            Column {
                Text("Current progress will be archived in history.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Khatam Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(15, 30, 60).forEach { days ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = { selectedDays = days },
                            label = { Text("$days d") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, selectedDays)
                    }
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MarkRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (start: Int, end: Int) -> Unit
) {
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark Ayah Range") },
        text = {
            Column {
                Text("Enter global Ayah numbers (1 to ${QuranAyahMetadata.TOTAL_AYAHS}):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("From Ayah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("To Ayah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = startText.toIntOrNull() ?: 1
                    val e = endText.toIntOrNull() ?: 1
                    if (s in 1..QuranAyahMetadata.TOTAL_AYAHS && e in 1..QuranAyahMetadata.TOTAL_AYAHS && s <= e) {
                        onConfirm(s, e)
                    }
                }
            ) {
                Text("Mark Complete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
