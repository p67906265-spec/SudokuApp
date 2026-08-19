package com.quaderno.sudoku

import android.os.Bundle
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

// Palette moderna della schermata di riferimento
internal val AppBlue = Color(0xFF2F63AD)
internal val AppBlueSoft = Color(0xFFE3EDF8)
internal val GridLine = Color(0xFF3E424B)
internal val CellLine = Color(0xFFC6CDD6)
internal val AppText = Color(0xFF727887)

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Color.White) {
                    SudokuAppRoot()
                }
            }
        }
    }
}
private enum class AppScreen { HOME, GAME, SETTINGS, TUTORIAL, STATISTICS, CHALLENGES, DAILY }

@Composable
private fun SudokuAppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val stats = remember { StatsStore(context) }
    val settings = remember { SettingsStore(context) }
    val resumePrefs = remember { context.getSharedPreferences("resume_game", Context.MODE_PRIVATE) }
    var hasResumeGame by remember { mutableStateOf(resumePrefs.getBoolean("active", false)) }
    var statsVersion by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var showLevels by remember { mutableStateOf(false) }
    var showDailyResumeDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showIntro by remember { mutableStateOf(true) }
    var completedDailyResult by remember { mutableStateOf<ChallengeResult?>(null) }
    var completedDailyDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingDailyDate by remember { mutableStateOf<LocalDate?>(null) }
    var dailyGameDate by remember { mutableStateOf<LocalDate?>(null) }
    var dailySelectFirstAvailable by remember { mutableStateOf(false) }
    var showDailyResultDialog by remember { mutableStateOf(false) }
    var dailyResultSeconds by remember { mutableStateOf(0) }
    var dailyResultScore by remember { mutableStateOf(0) }
    var newlyUnlockedLevel by remember { mutableStateOf<SudokuEngine.Difficulty?>(null) }
    val game = remember { GameState(SudokuEngine.Difficulty.MEDIO, settings) }

    fun clearResumeGame() {
        resumePrefs.edit().clear().apply()
        hasResumeGame = false
    }
    fun saveResumeGame() {
        if (game.won || game.failed()) { clearResumeGame(); return }
        val noteText = game.notes.joinToString("|") { it.sorted().joinToString("") }
        resumePrefs.edit()
            .putBoolean("active", true)
            .putInt("difficulty", game.difficulty.ordinal)
            .putString("code", game.gameCode)
            .putString("board", game.board.joinToString(","))
            .putString("notes", noteText)
            .putInt("mistakes", game.mistakes)
            .putInt("hints", game.hintsUsed)
            .putInt("seconds", game.seconds)
            .putBoolean("notesMode", game.notesMode)
            .apply()
        hasResumeGame = true
    }
    fun restoreResumeGame(): Boolean {
        if (!resumePrefs.getBoolean("active", false)) return false
        val code = resumePrefs.getString("code", null) ?: return false
        val board = resumePrefs.getString("board", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toIntArray()
        if (board.size != 81) { clearResumeGame(); return false }
        val notes = resumePrefs.getString("notes", "").orEmpty().split('|').let { parts ->
            List(81) { i -> parts.getOrNull(i).orEmpty().mapNotNull { ch -> ch.digitToIntOrNull() }.filter { it in 1..9 } }
        }
        val difficulty = SudokuEngine.Difficulty.values().getOrElse(resumePrefs.getInt("difficulty", 1)) { SudokuEngine.Difficulty.MEDIO }
        game.restoreSaved(difficulty, code, board, notes, resumePrefs.getInt("mistakes", 0), resumePrefs.getInt("hints", 0), resumePrefs.getInt("seconds", 0), resumePrefs.getBoolean("notesMode", false))
        dailyGameDate = null
        screen = AppScreen.GAME
        return true
    }

    LaunchedEffect(game.won, game.generation) {
        if (game.won) {
            clearResumeGame()
            val nextLevel = SudokuEngine.Difficulty.values().getOrNull(game.difficulty.ordinal + 1)
            val wasNextLevelUnlocked = nextLevel?.let(stats::isUnlocked) ?: true
            stats.complete(game)
            statsVersion++
            if (nextLevel != null && !wasNextLevelUnlocked && stats.isUnlocked(nextLevel)) {
                newlyUnlockedLevel = nextLevel
            }
            if (dailyGameDate != null) {
                dailyResultSeconds = game.seconds
                dailyResultScore = game.score(true)
                delay(3500)
                showDailyResultDialog = true
            }
        }
    }

    LaunchedEffect(game.generation, screen) {
        if (screen == AppScreen.GAME && !game.won) {
            stats.start(game.difficulty)
            statsVersion++
        }
    }

    when (screen) {
        AppScreen.HOME -> HomeScreen(
            onPlay = { showLevels = true },
            hasResume = hasResumeGame,
            onResume = { restoreResumeGame() },
            onDaily = {
                dailySelectFirstAvailable = false
                screen = AppScreen.DAILY
            },
            onSettings = { screen = AppScreen.SETTINGS },
            onTutorial = { screen = AppScreen.TUTORIAL },
            onStatistics = { screen = AppScreen.STATISTICS },
            onChallenges = { screen = AppScreen.CHALLENGES }
        )
        AppScreen.GAME -> SudokuScreen(
            game,
            statsVersion = statsVersion,
            onBack = {
                if (!game.won) showExitDialog = true
                else {
                    dailyGameDate = null
                    screen = AppScreen.HOME
                }
            },
            onSettings = {
                if (!game.won) stats.abandonActive()
                statsVersion++
                screen = AppScreen.SETTINGS
            },
            onChangeLevel = { showLevels = true }
        )
        AppScreen.SETTINGS -> SettingsScreen(settings) { screen = AppScreen.HOME }
        AppScreen.TUTORIAL -> TutorialScreen { screen = AppScreen.HOME }
        AppScreen.STATISTICS -> StatisticsScreen(stats, statsVersion) { screen = AppScreen.HOME }
        AppScreen.CHALLENGES -> ChallengeScreen(
            stats = stats,
            statsVersion = statsVersion,
            onBack = { screen = AppScreen.HOME },
            onPlayCode = { code ->
                ChallengeCodes.difficulty(code)?.let { level ->
                    game.reset(level, code)
                    screen = AppScreen.GAME
                }
            }
        )
        AppScreen.DAILY -> DailyChallengeScreen(
            stats = stats,
            statsVersion = statsVersion,
            selectFirstAvailable = dailySelectFirstAvailable,
            onBack = { screen = AppScreen.HOME },
            onCompleted = { date, result ->
                completedDailyDate = date
                completedDailyResult = result
            },
            onPlay = { code ->
                dailySelectFirstAvailable = false
                val selectedDailyDate = LocalDate.now().let { today ->
                    (1..today.dayOfMonth).map { YearMonth.from(today).atDay(it) }
                        .firstOrNull { ChallengeCodes.isDailyCode(it, code) }
                }
                if (selectedDailyDate != null &&
                    dailyGameDate == selectedDailyDate &&
                    game.gameCode == ChallengeCodes.normalize(code) &&
                    !game.won
                ) {
                    pendingDailyDate = selectedDailyDate
                    showDailyResumeDialog = true
                } else {
                    dailyGameDate = selectedDailyDate
                    game.reset(SudokuEngine.Difficulty.MEDIO, code)
                    screen = AppScreen.GAME
                }
            }
        )
    }

    if (showIntro) {
        SudokuIntroAnimation { showIntro = false }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Sei sicuro di uscire?", fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(
                    onClick = {
                        saveResumeGame()
                        showExitDialog = false
                        screen = AppScreen.HOME
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                ) {
                    Text("Sì", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text("No", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    completedDailyResult?.let { result ->
        AlertDialog(
            onDismissRequest = { completedDailyResult = null; completedDailyDate = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color.White,
            title = { Text("Gioco del giorno completato", fontWeight = FontWeight.Bold, color = Color(0xFF263A58)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(completedDailyDate?.toString().orEmpty(), color = AppText)
                    ResultLine("Difficoltà", ChallengeCodes.difficulty(result.code)?.label ?: "Medio")
                    ResultLine("Tempo", formatTime(result.seconds))
                    ResultLine("Punteggio", result.score.toString())
                }
            },
            confirmButton = { TextButton(onClick = { completedDailyResult = null; completedDailyDate = null }) { Text("Chiudi") } }
        )
    }

    if (showLevels) {
        DifficultyDialog(
            stats = stats,
            statsVersion = statsVersion,
            onDismiss = { showLevels = false },
            onSelected = {
                clearResumeGame()
                game.reset(it)
                showLevels = false
                screen = AppScreen.GAME
            }
        )
    }

    if (showDailyResumeDialog) {
        AlertDialog(
            onDismissRequest = {
                pendingDailyDate = null
                showDailyResumeDialog = false
            },
            title = { Text("Sfida del giorno", fontWeight = FontWeight.Bold) },
            text = { Text("Cosa vuoi fare con la partita in corso?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDailyDate = null
                    showDailyResumeDialog = false
                    screen = AppScreen.GAME
                }) { Text("Continua la partita") }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        val date = pendingDailyDate
                        if (date != null) {
                            dailyGameDate = date
                            game.reset(game.difficulty, game.gameCode)
                            screen = AppScreen.GAME
                        }
                        pendingDailyDate = null
                        showDailyResumeDialog = false
                    }) { Text("Ricomincia") }
                    TextButton(onClick = {
                        pendingDailyDate = null
                        showDailyResumeDialog = false
                    }) { Text("Annulla") }
                }
            }
        )
    }

    if (showDailyResultDialog) {
        AlertDialog(
            onDismissRequest = {
                showDailyResultDialog = false
                dailyGameDate = null
                dailySelectFirstAvailable = true
                screen = AppScreen.DAILY
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
            title = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✓", color = AppBlue, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text("Sfida completata!", color = Color(0xFF263A58), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tempo", color = AppText, fontSize = 15.sp)
                            Text(formatTime(dailyResultSeconds), color = Color(0xFF263A58), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Punteggio", color = AppText, fontSize = 15.sp)
                            Text("$dailyResultScore", color = AppBlue, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDailyResultDialog = false
                        dailyGameDate = null
                        dailySelectFirstAvailable = true
                        screen = AppScreen.DAILY
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                ) { Text("Torna alle sfide", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        )
    }

    newlyUnlockedLevel?.let { level ->
        UnlockOverlay(
            level = level,
            onTryLevel = {
                newlyUnlockedLevel = null
                showDailyResultDialog = false
                dailyGameDate = null
                game.reset(level)
                screen = AppScreen.GAME
            },
            onContinue = { newlyUnlockedLevel = null }
        )
    }
}

@Composable
private fun UnlockOverlay(
    level: SudokuEngine.Difficulty,
    onTryLevel: () -> Unit,
    onContinue: () -> Unit
) {
    BackHandler(onBack = onContinue)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(82.dp).background(AppBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("★", color = Color.White, fontSize = 47.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Congratulazioni!",
                    color = Color(0xFF263A58),
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Hai sbloccato il livello",
                    color = AppText,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    level.label.lowercase().replaceFirstChar { it.uppercase() },
                    color = AppBlue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(26.dp))
                Button(
                    onClick = onTryLevel,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                ) {
                    Text("Prova il nuovo livello", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppBlue)
                ) {
                    Text("Continua", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onPlay: () -> Unit,
    hasResume: Boolean,
    onResume: () -> Unit,
    onDaily: () -> Unit,
    onSettings: () -> Unit,
    onTutorial: () -> Unit,
    onStatistics: () -> Unit,
    onChallenges: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F8FC)).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(52.dp))
        Text("Sudoku Free", color = Color(0xFF171A22), fontSize = 43.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        Text("gioca, rilassati, divertiti", color = AppBlue, fontSize = 19.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 2.dp, color = Color(0xFF2B2F38))
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().height(62.dp).border(1.5.dp, Color(0xFF222833), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356DB9))
        ) { Text("G I O C A", fontSize = 19.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onResume,
            enabled = hasResume,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (hasResume) Color(0xFF356DB9) else Color.Transparent,
                contentColor = if (hasResume) Color.White else AppBlue,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = AppText.copy(alpha = 0.45f)
            )
        ) { Text("R I P R E N D I", fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(18.dp))
        val today = LocalDate.now()
        val monthNames = listOf(
            "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
            "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(92.dp)
                .background(Color(0xFFEDF5FD), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFD1E0F0), RoundedCornerShape(16.dp))
                .clickable { onDaily() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("▦", color = AppBlue, fontSize = 45.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Sfida del giorno", color = Color(0xFF1D2738), fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${today.dayOfMonth} ${monthNames[today.monthValue - 1]}", color = AppBlue, fontSize = 16.sp)
            }
            Box(Modifier.width(1.dp).height(54.dp).background(Color(0xFFD2DDEB)))
            Spacer(Modifier.width(14.dp))
            Text("Apri", color = AppBlue, fontSize = 17.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.width(8.dp))
            Text("→", color = AppBlue, fontSize = 28.sp)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeGridCard("▥", "Statistiche", onStatistics)
            HomeGridCard("#", "Sfide con codice", onChallenges)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeGridCard("⚙", "Impostazioni", onSettings)
            HomeGridCard("?", "Come si gioca", onTutorial, circledIcon = true)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RowScope.HomeGridCard(
    icon: String,
    title: String,
    onClick: () -> Unit,
    circledIcon: Boolean = false
) {
    Column(
        modifier = Modifier.weight(1f).height(124.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE1E4E9), RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = if (circledIcon) Modifier.size(48.dp).border(1.8.dp, AppBlue, CircleShape) else Modifier.height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = AppBlue, fontSize = if (icon == "#") 42.sp else 34.sp, fontWeight = FontWeight.Normal)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = Color(0xFF202634),
            fontSize = 16.sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun DailyChallengeScreen(
    stats: StatsStore,
    statsVersion: Int,
    selectFirstAvailable: Boolean,
    onBack: () -> Unit,
    onCompleted: (LocalDate, ChallengeResult) -> Unit,
    onPlay: (String) -> Unit
) {
    statsVersion
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val monthNames = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )
    val completedDays = (1..month.lengthOfMonth()).filter { day ->
        val date = month.atDay(day)
        ChallengeCodes.dailyCodes(date).any { stats.challengeResult(it) != null }
    }.toSet()
    var selectedDate by remember(month, statsVersion, selectFirstAvailable) {
        mutableStateOf(
            if (today.dayOfMonth !in completedDays) {
                today
            } else {
                ((today.dayOfMonth - 1) downTo 1)
                    .firstOrNull { it !in completedDays }
                    ?.let(month::atDay)
            }
        )
    }
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val calendarCells = firstOffset + month.lengthOfMonth()
    val rows = (calendarCells + 6) / 7

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(
            Modifier.fillMaxWidth().height(235.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF2F83DC), Color(0xFF46B7EB))))
        ) {
            Text("‹", color = Color.White, fontSize = 50.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 20.dp).clickable { onBack() })
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sfida del giorno", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("🏆", fontSize = 76.sp)
                Text(
                    selectedDate?.let { "${it.dayOfMonth} ${monthNames[it.monthValue - 1]} ${it.year}" } ?: "Mese completato",
                    color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${monthNames[month.monthValue - 1]} ${month.year}", color = Color(0xFF20242D), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("★", color = Color(0xFFFFB51B), fontSize = 24.sp)
                Spacer(Modifier.width(6.dp))
                Text("${completedDays.size}/${month.lengthOfMonth()}", color = Color(0xFF20242D), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "G", "V", "S", "D").forEach { label ->
                    Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color(0xFF9AA0AC), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth().height(42.dp)) {
                    repeat(7) { col ->
                        val cell = row * 7 + col
                        val day = cell - firstOffset + 1
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val completed = day in completedDays
                                val isToday = day == today.dayOfMonth
                                val isFuture = day > today.dayOfMonth
                                val isSelected = selectedDate?.dayOfMonth == day
                                Box(
                                    Modifier.size(39.dp)
                                        .then(
                                            when {
                                                completed -> Modifier.background(Color(0xFFFFC32D), CircleShape)
                                                isSelected -> Modifier.background(Color(0xFF358DE5), CircleShape)
                                                isToday -> Modifier.border(2.dp, Color(0xFF358DE5), CircleShape)
                                                else -> Modifier
                                            }
                                        )
                                        .then(
                                            when {
                                                completed -> Modifier.clickable {
                                                    val date = month.atDay(day)
                                                    val result = ChallengeCodes.dailyCodes(date).mapNotNull { stats.challengeResult(it) }.firstOrNull()
                                                    if (result != null) onCompleted(date, result)
                                                }
                                                !isFuture -> Modifier.clickable { selectedDate = month.atDay(day) }
                                                else -> Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (completed) "★" else "$day",
                                        color = when {
                                            completed -> Color(0xFFFF8A00)
                                            isSelected -> Color.White
                                            isToday -> Color(0xFF358DE5)
                                            isFuture -> Color(0xFFC4C7CE)
                                            else -> Color(0xFF747B8A)
                                        },
                                        fontSize = if (completed) 23.sp else 17.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { selectedDate?.let { onPlay(stats.dailyCode(it)) } },
                enabled = selectedDate != null,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(29.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF358DE5))
            ) {
                Text(if (selectedDate == null) "Sfide completate" else "Gioca", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppText, fontWeight = FontWeight.SemiBold)
        Text(value, color = AppBlue, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SudokuIntroAnimation(onFinished: () -> Unit) {
    var filled by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(18) { delay(65); filled++ }
        delay(350)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFF7F8FC)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sudoku Free", color = AppBlue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.size(216.dp).border(2.dp, GridLine)) {
                repeat(9) { r ->
                    Row(Modifier.weight(1f)) {
                        repeat(9) { c ->
                            val i = r * 9 + c
                            val show = ((i * 17 + 11) % 81) < filled * 3
                            Box(Modifier.weight(1f).fillMaxHeight().border(.35.dp, CellLine), contentAlignment = Alignment.Center) {
                                if (show) Text("${(r * 3 + r / 3 + c) % 9 + 1}", color = AppBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Preparati a giocare", color = AppText, fontSize = 16.sp)
        }
    }
}

@Composable
private fun DifficultyDialog(
    stats: StatsStore,
    statsVersion: Int,
    onDismiss: () -> Unit,
    onSelected: (SudokuEngine.Difficulty) -> Unit
) {
    statsVersion
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text("Scegli il livello", color = Color(0xFF25344B), fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SudokuEngine.Difficulty.values().forEachIndexed { index, level ->
                    val unlocked = stats.isUnlocked(level)
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (unlocked) Modifier.clickable { onSelected(level) } else Modifier)
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).background(if (unlocked) AppBlueSoft else Color(0xFFE9EBEF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (unlocked) "${index + 1}" else "🔒", color = if (unlocked) AppBlue else AppText, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                level.label.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (unlocked) AppBlue else AppText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!unlocked) {
                                val previous = SudokuEngine.Difficulty.values()[level.ordinal - 1]
                                Text(
                                    "${stats.unlockProgress(level)}/5 partite ${previous.label.lowercase()} completate",
                                    color = AppText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (index < SudokuEngine.Difficulty.values().lastIndex) {
                        HorizontalDivider(color = Color(0xFFE5E8EC))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = AppText) } }
    )
}

@Composable
private fun SimplePageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = AppBlue, fontSize = 48.sp, modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.weight(1f))
        Text(title, color = Color(0xFF17243A), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(28.dp))
    }
}

@Composable
private fun ChallengeScreen(
    stats: StatsStore,
    statsVersion: Int,
    onBack: () -> Unit,
    onPlayCode: (String) -> Unit
) {
    statsVersion
    val context = androidx.compose.ui.platform.LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var previousResult by remember { mutableStateOf<ChallengeResult?>(null) }
    var generatedCode by remember { mutableStateOf("") }
    var challengeLevel by remember { mutableStateOf(SudokuEngine.Difficulty.MEDIO) }
    var showChallengeLevelPicker by remember { mutableStateOf(false) }
    val history = stats.challengeHistory()

    fun requestPlay(rawCode: String) {
        val normalized = ChallengeCodes.normalize(rawCode)
        if (ChallengeCodes.difficulty(normalized) == null) {
            error = "Codice non valido"
            return
        }
        error = ""
        val result = stats.challengeResult(normalized)
        if (result != null) previousResult = result else onPlayCode(normalized)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF0F3F9))) {
        SimplePageHeader("Sfide con codice", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(20.dp)) {
                Text("Crea una sfida", color = Color(0xFF25344B), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Genera un codice e invialo a chi vuoi sfidare.", color = AppText, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showChallengeLevelPicker = true },
                    shape = RoundedCornerShape(15.dp),
                    color = AppBlueSoft
                ) {
                    Row(Modifier.padding(horizontal = 17.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Livello", color = AppText, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            challengeLevel.label.lowercase().replaceFirstChar { it.uppercase() },
                            color = AppBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold
                        )
                        Text("  ⌄", color = AppBlue, fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { generatedCode = ChallengeCodes.create(challengeLevel) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                ) { Text("GENERA CODICE", fontWeight = FontWeight.Bold) }

                if (generatedCode.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().background(Color(0xFFF2F6FC), RoundedCornerShape(17.dp)).padding(17.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CODICE DELLA SFIDA", color = AppText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(generatedCode, color = AppBlue, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onPlayCode(generatedCode) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Gioca", color = AppBlue, fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = {
                                val levelName = challengeLevel.label.lowercase().replaceFirstChar { it.uppercase() }
                                val message = "Ti sfido a Sudoku Free! Livello $levelName. Inserisci il codice $generatedCode in Sfide con codice e giochiamo lo stesso schema."
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message)
                                }
                                context.startActivity(Intent.createChooser(intent, "Condividi codice"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                        ) { Text("Condividi codice", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(20.dp)) {
                Text("Gioca lo stesso schema", color = Color(0xFF25344B), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Inserisci il codice ricevuto da un amico.", color = AppText, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(9); error = "" },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Codice schema") },
                    placeholder = { Text("Esempio: ME-7K4P9X") },
                    isError = error.isNotEmpty()
                )
                if (error.isNotEmpty()) Text(error, color = Color(0xFFD14A4A), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { requestPlay(code) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                ) { Text("GIOCA CON QUESTO CODICE", fontWeight = FontWeight.Bold) }
            }

            Text("Schemi completati", color = Color(0xFF25344B), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text("Non hai ancora completato schemi con un codice.", color = AppText, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(18.dp))
            } else {
                history.forEach { result ->
                    Row(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))
                            .clickable { previousResult = result }.padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).background(AppBlueSoft, CircleShape), contentAlignment = Alignment.Center) {
                            Text("#", color = AppBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.code, color = Color(0xFF25344B), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Miglior tempo ${formatTime(result.seconds)}  •  ${result.score} punti", color = AppText, fontSize = 13.sp)
                        }
                        Text("›", color = AppText, fontSize = 30.sp)
                    }
                }
            }
        }
    }

    previousResult?.let { result ->
        AlertDialog(
            onDismissRequest = { previousResult = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color.White,
            title = { Text("Schema già completato", color = Color(0xFF25344B), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(result.code, color = AppBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Questo schema è stato finito in ${formatTime(result.seconds)}.", color = AppText, fontSize = 16.sp)
                    Text("Miglior punteggio: ${result.score}  •  Tentativi: ${result.attempts}", color = AppText, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    previousResult = null
                    onPlayCode(result.code)
                }, colors = ButtonDefaults.buttonColors(containerColor = AppBlue)) { Text("Rigioca") }
            },
            dismissButton = { TextButton(onClick = { previousResult = null }) { Text("Annulla") } }
        )
    }

    if (showChallengeLevelPicker) {
        AlertDialog(
            onDismissRequest = { showChallengeLevelPicker = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color.White,
            title = { Text("Livello della sfida", color = Color(0xFF25344B), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    SudokuEngine.Difficulty.values().forEach { level ->
                        val unlocked = stats.isUnlocked(level)
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (unlocked) Modifier.clickable {
                                    challengeLevel = level
                                    generatedCode = ""
                                    showChallengeLevelPicker = false
                                } else Modifier)
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                level.label.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (unlocked) Color(0xFF25344B) else AppText,
                                fontSize = 18.sp,
                                fontWeight = if (level == challengeLevel) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(Modifier.weight(1f))
                            if (!unlocked) Text("🔒")
                            if (level == challengeLevel) Text("✓", color = AppBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showChallengeLevelPicker = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun StatisticsScreen(stats: StatsStore, statsVersion: Int, onBack: () -> Unit) {
    statsVersion
    val levels = SudokuEngine.Difficulty.values()
    var selectedLevel by remember { mutableStateOf(SudokuEngine.Difficulty.FACILE) }
    var showLevelPicker by remember { mutableStateOf(false) }
    val all = levels.map { stats.stats(it) }
    val totalCompleted = all.sumOf { it.completed }
    val totalSeconds = all.sumOf { it.totalSeconds }
    val selected = stats.stats(selectedLevel)
    val average = if (selected.completed == 0) 0 else (selected.totalSeconds / selected.completed).toInt()

    Column(Modifier.fillMaxSize().background(Color(0xFFF0F3F9))) {
        SimplePageHeader("Statistiche", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Completate", totalCompleted.toString(), Modifier.weight(1f))
                SummaryCard("Tempo totale", formatDuration(totalSeconds), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Serie attuale", stats.currentStreak().toString(), Modifier.weight(1f))
                SummaryCard("Serie migliore", stats.bestStreak().toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("Dettaglio per livello", color = Color(0xFF25344B), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showLevelPicker = true },
                shape = RoundedCornerShape(18.dp),
                color = AppBlue
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedLevel.label.lowercase().replaceFirstChar { it.uppercase() },
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    if (!stats.isUnlocked(selectedLevel)) Text("🔒  ", fontSize = 17.sp)
                    Text("⌄", color = Color.White, fontSize = 26.sp)
                }
            }

            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(18.dp)) {
                if (!stats.isUnlocked(selectedLevel)) {
                    Text("Livello ancora bloccato", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = stats.unlockProgress(selectedLevel) / 5f,
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),
                        color = AppBlue,
                        trackColor = AppBlueSoft
                    )
                    Text("${stats.unlockProgress(selectedLevel)}/5 vittorie richieste", color = AppText, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LevelStatCard("Iniziate", selected.started.toString(), Modifier.weight(1f))
                    LevelStatCard("Completate", selected.completed.toString(), Modifier.weight(1f))
                    LevelStatCard("Abbandonate", selected.abandoned.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LevelStatCard("Tempo migliore", formatTime(selected.bestSeconds), Modifier.weight(1f))
                    LevelStatCard("Tempo medio", formatTime(average), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LevelStatCard("Miglior punteggio", selected.bestScore.toString(), Modifier.weight(1f))
                    LevelStatCard("Senza errori", selected.flawless.toString(), Modifier.weight(1f))
                }
            }
        }
    }

    if (showLevelPicker) {
        AlertDialog(
            onDismissRequest = { showLevelPicker = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color.White,
            title = { Text("Scegli il livello", color = Color(0xFF25344B), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    levels.forEach { level ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedLevel = level
                                showLevelPicker = false
                            }.padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                level.label.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (level == selectedLevel) AppBlue else Color(0xFF25344B),
                                fontSize = 18.sp,
                                fontWeight = if (level == selectedLevel) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(Modifier.weight(1f))
                            if (!stats.isUnlocked(level)) Text("🔒")
                            if (level == selectedLevel) Text("  ✓", color = AppBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLevelPicker = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun LevelStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(AppBlueSoft, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = AppBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = AppText, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = AppBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AppText, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private fun formatTime(seconds: Int): String =
    if (seconds <= 0) "—" else "%02d:%02d".format(seconds / 60, seconds % 60)

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "0 min"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes.coerceAtLeast(1)} min"
}

@Composable
private fun SettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF0F3F9))) {
        SimplePageHeader("Impostazioni", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingToggle("Animazioni", settings.animations, settings::updateAnimations)
            SettingToggle("Suggerimenti intelligenti", settings.smartHints, settings::updateSmartHints)
            SettingToggle("Limite di 3 errori", settings.errorLimit, settings::updateErrorLimit)
            Text("Le preferenze vengono salvate e applicate subito.", color = AppText, fontSize = 14.sp, modifier = Modifier.padding(10.dp))
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFD5DCE8))
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Paolo Free 1.0", color = AppBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Sudoku Free – Versione 1.32", color = AppText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color(0xFF202A38), fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedTrackColor = AppBlue))
    }
}

@Composable
private fun TutorialScreen(onBack: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val bodies = listOf(
        "Un Sudoku si completa quando ogni numero da 1 a 9 appare una sola volta in ogni riga, colonna e riquadro 3×3.",
        "Seleziona una casella vuota e tocca un numero per inserirlo.",
        "Attiva Note per aggiungere o rimuovere i possibili numeri nelle caselle."
    )
    Column(Modifier.fillMaxSize().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally) {
        SimplePageHeader("Come si gioca", onBack)
        TutorialVisual(page)
        Spacer(Modifier.height(14.dp))
        Text(bodies[page], color = Color(0xFF303442), fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 28.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Salta", color = AppBlue, fontSize = 17.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { dot -> Box(Modifier.size(9.dp).background(if (dot == page) AppBlue else Color(0xFFB7BAC0), CircleShape)) }
            }
            Spacer(Modifier.weight(1f))
            Text(if (page < 2) "Prossimo" else "Inizia", color = AppBlue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { if (page < 2) page++ else onBack() })
        }
    }
}

@Composable
private fun TutorialVisual(page: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TutorialBoard(page)
        if (page > 0) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                TutorialTool("↶", "Annulla")
                TutorialTool("▱", "Cancella")
                TutorialTool(if (page == 2) "✎ ON" else "✎ OFF", "Note", page == 2)
                TutorialTool("♧", "Suggerimento")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (n in 1..9) Text("$n", color = if (page == 2) Color(0xFFB8C0CF) else AppBlue, fontSize = 27.sp)
            }
            Text(
                if (page == 1) "Tocca il numero per riempire la casella selezionata" else "Attiva le Note per segnare più possibilità",
                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.background(Color(0xE62D3043), RoundedCornerShape(15.dp)).padding(horizontal = 20.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun TutorialTool(icon: String, label: String, active: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = if (active) AppBlue else Color(0xFF343849), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AppText, fontSize = 10.sp)
    }
}

@Composable
private fun TutorialBoard(page: Int) {
    val solved = intArrayOf(
        1,2,5,9,3,7,8,4,6, 9,8,6,4,5,1,3,2,7, 7,3,4,6,8,2,1,9,5,
        5,1,8,3,7,4,9,6,2, 4,9,3,1,2,6,7,5,8, 2,6,7,5,9,8,4,1,3,
        8,7,9,2,4,5,6,3,1, 3,5,1,7,6,9,2,8,4, 6,4,2,8,1,3,5,7,9
    )
    val visible = setOf(0,1,2,9,20,22,24,25,35,39,41,43,44,45,47,49,51,58,61,62,66,73,74,75,79)
    Column(Modifier.fillMaxWidth().aspectRatio(1f).border(2.dp, Color(0xFF202437))) {
        for (r in 0..8) {
            Row(Modifier.weight(1f)) {
                for (c in 0..8) {
                    val pos = r * 9 + c
                    val selected = page > 0 && pos == 20
                    val highlighted = page == 0 && (r == 2 || c == 3 || (r >= 6 && c >= 6))
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(if (selected) Color(0xFFAFC8FF) else if (highlighted) Color(0xFFE4E8F4) else Color.White)
                            .border(0.5.dp, Color(0xFFC8CDD6))
                            .thickEdge(right = c == 2 || c == 5, bottom = r == 2 || r == 5),
                        contentAlignment = Alignment.Center
                    ) {
                        if (page == 2 && pos == 20) {
                            NotesGrid(listOf(2,4,6,7,9))
                        } else if (page == 0 || pos in visible) {
                            Text("${solved[pos]}", color = if ((pos + r) % 3 == 0) AppBlue else Color(0xFF202437), fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
