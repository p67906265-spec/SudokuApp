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
private val AppBlue = Color(0xFF2F63AD)
private val AppBlueSoft = Color(0xFFE3EDF8)
private val GridLine = Color(0xFF3E424B)
private val CellLine = Color(0xFFC6CDD6)
private val AppText = Color(0xFF727887)

// ---------------------------------------------------------------------------
// Motore Sudoku: generazione griglia piena, rimozione celle con verifica
// di soluzione unica (backtracking con bitmask, stessa logica per ogni livello)
// ---------------------------------------------------------------------------
object SudokuEngine {

    enum class Difficulty(val label: String, val clues: Int) {
        FACILE("FACILE", 40),
        MEDIO("MEDIO", 33),
        DIFFICILE("DIFFICILE", 27),
        ESPERTO("ESPERTO", 23),
        MASTER("MASTER", 21),
        ESTREMO("ESTREMO", 19)
    }

    data class Puzzle(val given: IntArray, val solution: IntArray)

    private fun boxOf(r: Int, c: Int) = (r / 3) * 3 + (c / 3)

    private fun generateFullGrid(random: Random): IntArray {
        val grid = IntArray(81)
        val rowMask = IntArray(9)
        val colMask = IntArray(9)
        val boxMask = IntArray(9)

        fun fill(pos: Int): Boolean {
            if (pos == 81) return true
            val r = pos / 9
            val c = pos % 9
            val b = boxOf(r, c)
            val used = rowMask[r] or colMask[c] or boxMask[b]
            val nums = (1..9).shuffled(random)
            for (n in nums) {
                val bit = 1 shl n
                if (used and bit != 0) continue
                grid[pos] = n
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
                if (fill(pos + 1)) return true
                rowMask[r] = rowMask[r] and bit.inv()
                colMask[c] = colMask[c] and bit.inv()
                boxMask[b] = boxMask[b] and bit.inv()
                grid[pos] = 0
            }
            return false
        }
        fill(0)
        return grid
    }

    /** Conta le soluzioni fino a `limit` (usato per verificare l'unicità: basta sapere se sono 1 o >=2). */
    private fun countSolutions(start: IntArray, limit: Int): Int {
        val g = start.copyOf()
        val rowMask = IntArray(9)
        val colMask = IntArray(9)
        val boxMask = IntArray(9)
        for (p in 0 until 81) {
            if (g[p] != 0) {
                val r = p / 9; val c = p % 9; val b = boxOf(r, c); val bit = 1 shl g[p]
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
            }
        }
        var count = 0

        fun findBestCell(): Triple<Int, Int, Int> {
            var best = -1; var bestCount = 10; var bestCands = 0
            for (p in 0 until 81) {
                if (g[p] != 0) continue
                val r = p / 9; val c = p % 9; val b = boxOf(r, c)
                val used = rowMask[r] or colMask[c] or boxMask[b]
                var cands = 0; var n = 0
                for (v in 1..9) if (used and (1 shl v) == 0) { cands = cands or (1 shl v); n++ }
                if (n < bestCount) { bestCount = n; best = p; bestCands = cands; if (n == 0) break }
            }
            return Triple(best, bestCount, bestCands)
        }

        fun solve() {
            if (count >= limit) return
            val (pos, cnt, cands) = findBestCell()
            if (pos == -1) { count++; return }
            if (cnt == 0) return
            val r = pos / 9; val c = pos % 9; val b = boxOf(r, c)
            for (v in 1..9) {
                val bit = 1 shl v
                if (cands and bit == 0) continue
                g[pos] = v
                rowMask[r] = rowMask[r] or bit; colMask[c] = colMask[c] or bit; boxMask[b] = boxMask[b] or bit
                solve()
                rowMask[r] = rowMask[r] and bit.inv(); colMask[c] = colMask[c] and bit.inv(); boxMask[b] = boxMask[b] and bit.inv()
                g[pos] = 0
                if (count >= limit) return
            }
        }
        solve()
        return count
    }

    fun generatePuzzle(difficulty: Difficulty, seed: Int = Random.nextInt()): Puzzle {
        val random = Random(seed)
        val full = generateFullGrid(random)
        val puzzle = full.copyOf()
        val target = difficulty.clues
        val order = (0 until 81).shuffled(random)
        var clues = 81
        for (pos in order) {
            if (clues <= target) break
            val backup = puzzle[pos]
            if (backup == 0) continue
            puzzle[pos] = 0
            val solCount = countSolutions(puzzle, 2)
            if (solCount == 1) clues-- else puzzle[pos] = backup
        }
        return Puzzle(puzzle, full)
    }
}

private object ChallengeCodes {
    private const val CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val prefixes = mapOf(
        SudokuEngine.Difficulty.FACILE to "FA",
        SudokuEngine.Difficulty.MEDIO to "ME",
        SudokuEngine.Difficulty.DIFFICILE to "DI",
        SudokuEngine.Difficulty.ESPERTO to "ES",
        SudokuEngine.Difficulty.MASTER to "MA",
        SudokuEngine.Difficulty.ESTREMO to "EX"
    )

    fun create(level: SudokuEngine.Difficulty): String {
        val body = buildString { repeat(6) { append(CHARS.random()) } }
        return "${prefixes.getValue(level)}-$body"
    }

    fun normalize(value: String) = value.trim().uppercase().replace(" ", "")

    fun difficulty(code: String): SudokuEngine.Difficulty? {
        val normalized = normalize(code)
        val parts = normalized.split('-')
        if (parts.size != 2 || parts[1].length != 6 || parts[1].any { it !in CHARS }) return null
        return prefixes.entries.firstOrNull { it.value == normalized.substringBefore('-') }?.key
    }

    fun seed(code: String): Int = normalize(code).hashCode()

    private fun dailyBody(date: LocalDate): String {
        val digits = "%02d%02d%02d".format(date.year % 100, date.monthValue, date.dayOfMonth)
        return digits.map { digit ->
            when (digit) {
                '0' -> 'A'
                '1' -> 'B'
                else -> digit
            }
        }.joinToString("")
    }

    fun daily(date: LocalDate, unlockedLevels: List<SudokuEngine.Difficulty>): String {
        val levels = unlockedLevels.ifEmpty { listOf(SudokuEngine.Difficulty.FACILE) }
        val random = Random(date.toEpochDay().toInt() xor 0x5D0D0)
        val level = levels[random.nextInt(levels.size)]
        return "${prefixes.getValue(level)}-${dailyBody(date)}"
    }

    fun dailyCodes(date: LocalDate): Set<String> =
        prefixes.values.map { "$it-${dailyBody(date)}" }.toSet() + legacyDaily(date)

    fun isDailyCode(date: LocalDate, code: String): Boolean = normalize(code) in dailyCodes(date)

    fun legacyDaily(date: LocalDate): String {
        val random = Random(date.toEpochDay().toInt())
        val body = buildString { repeat(6) { append(CHARS[random.nextInt(CHARS.length)]) } }
        return "ME-$body"
    }
}

// ---------------------------------------------------------------------------
// Stato di partita
// ---------------------------------------------------------------------------
class GameState(difficulty: SudokuEngine.Difficulty, private val settings: SettingsStore) {
    var difficulty by mutableStateOf(difficulty)
    var given: BooleanArray = BooleanArray(81)
    var solution: IntArray = IntArray(81)
    var gameCode by mutableStateOf("")
    private var startingBoard: IntArray = IntArray(81)
    val board: SnapshotStateList<Int> = mutableStateListOf()
    val notes: List<SnapshotStateList<Int>> = List(81) { mutableStateListOf() }
    var selected by mutableStateOf(-1)
    var notesMode by mutableStateOf(false)
    var mistakes by mutableStateOf(0)
    var hintsUsed by mutableStateOf(0)
    var autoCompleted by mutableStateOf(false)
    var seconds by mutableStateOf(0)
    var won by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var generation by mutableStateOf(0)
    val celebrationCells: SnapshotStateList<Int> = mutableStateListOf()
    var celebrationId by mutableStateOf(0)
    var celebrationOrigin by mutableStateOf(-1)
    var celebrationStep by mutableStateOf(-1)
    private val history = ArrayDeque<HistoryEntry>()

    private data class HistoryEntry(val pos: Int, val value: Int, val notes: List<Int>)

    init { reset(difficulty) }

    fun reset(newDifficulty: SudokuEngine.Difficulty = difficulty, requestedCode: String? = null) {
        val code = requestedCode?.let(ChallengeCodes::normalize) ?: ChallengeCodes.create(newDifficulty)
        val codeDifficulty = ChallengeCodes.difficulty(code) ?: newDifficulty
        difficulty = codeDifficulty
        gameCode = code
        val puzzle = SudokuEngine.generatePuzzle(codeDifficulty, ChallengeCodes.seed(code))
        given = BooleanArray(81) { puzzle.given[it] != 0 }
        solution = puzzle.solution
        startingBoard = puzzle.given.copyOf()
        board.clear(); board.addAll(puzzle.given.toList())
        notes.forEach { it.clear() }
        selected = -1
        notesMode = false
        mistakes = 0
        hintsUsed = 0
        autoCompleted = false
        seconds = 0
        won = false
        paused = false
        celebrationCells.clear()
        celebrationOrigin = -1
        celebrationStep = -1
        generation++
        history.clear()
    }

    fun retry() {
        board.clear(); board.addAll(startingBoard.toList())
        notes.forEach { it.clear() }
        selected = -1
        notesMode = false
        mistakes = 0
        hintsUsed = 0
        autoCompleted = false
        seconds = 0
        won = false
        paused = false
        celebrationCells.clear()
        celebrationOrigin = -1
        celebrationStep = -1
        generation++
        history.clear()
    }

    private fun pushHistory(pos: Int) {
        history.addLast(HistoryEntry(pos, board[pos], notes[pos].toList()))
        if (history.size > 200) history.removeFirst()
    }

    fun select(pos: Int) { if (!won && !failed()) selected = pos }

    private fun boxPositions(pos: Int): IntRange {
        val startRow = (pos / 9 / 3) * 3
        val startCol = (pos % 9 / 3) * 3
        val first = startRow * 9 + startCol
        return first..(first + 20)
    }

    private fun isInSameBox(origin: Int, candidate: Int): Boolean =
        origin / 9 / 3 == candidate / 9 / 3 && origin % 9 / 3 == candidate % 9 / 3

    private fun removeNoteFromBox(pos: Int, n: Int) {
        boxPositions(pos).forEach { candidate ->
            if (candidate in 0 until 81 && isInSameBox(pos, candidate)) notes[candidate].remove(n)
        }
    }

    private fun numberAlreadyInBox(pos: Int, n: Int): Boolean =
        (0 until 81).any { it != pos && isInSameBox(pos, it) && board[it] == n }

    fun input(n: Int) {
        if (won || failed()) return
        val pos = selected
        if (pos == -1 || given[pos]) return

        if (notesMode) {
            if (board[pos] != 0) return
            if (numberAlreadyInBox(pos, n)) return
            pushHistory(pos)
            if (notes[pos].contains(n)) notes[pos].remove(n) else notes[pos].add(n)
            return
        }

        val row = pos / 9
        val col = pos % 9
        val box = (row / 3) * 3 + (pos % 9 / 3)
        val rowWasComplete = isRowComplete(row)
        val columnWasComplete = isColumnComplete(col)
        val boxWasComplete = isBoxComplete(box)

        pushHistory(pos)
        if (board[pos] == n) {
            board[pos] = 0
        } else {
            board[pos] = n
            notes[pos].clear()
            removeNoteFromBox(pos, n)
            if (solution[pos] != n) mistakes++
        }
        checkWin()
        showCompletedArea(pos, row, col, box, rowWasComplete, columnWasComplete, boxWasComplete)
        if (!won && board[pos] == n && placedCount(n) >= 9) {
            selectNextIncompleteNumber(n)
        }
    }

    private fun isRowComplete(row: Int): Boolean =
        (0..8).all { col -> board[row * 9 + col] == solution[row * 9 + col] }

    private fun isColumnComplete(col: Int): Boolean =
        (0..8).all { row -> board[row * 9 + col] == solution[row * 9 + col] }

    private fun isBoxComplete(box: Int): Boolean {
        val firstRow = (box / 3) * 3
        val firstCol = (box % 3) * 3
        return (0..2).all { dr ->
            (0..2).all { dc ->
                val cell = (firstRow + dr) * 9 + firstCol + dc
                board[cell] == solution[cell]
            }
        }
    }

    private fun showCompletedArea(
        origin: Int,
        row: Int,
        col: Int,
        box: Int,
        rowWasComplete: Boolean,
        columnWasComplete: Boolean,
        boxWasComplete: Boolean
    ) {
        val cells = linkedSetOf<Int>()
        if (!rowWasComplete && isRowComplete(row)) {
            (0..8).forEach { col -> cells += row * 9 + col }
        }
        if (!columnWasComplete && isColumnComplete(col)) {
            (0..8).forEach { row -> cells += row * 9 + col }
        }
        if (!boxWasComplete && isBoxComplete(box)) {
            val firstRow = (box / 3) * 3
            val firstCol = (box % 3) * 3
            (0..2).forEach { dr -> (0..2).forEach { dc -> cells += (firstRow + dr) * 9 + firstCol + dc } }
        }
        if (cells.isNotEmpty() && settings.animations) {
            celebrationCells.clear()
            celebrationCells.addAll(cells)
            celebrationOrigin = origin
            celebrationStep = 0
            celebrationId++
        }
    }

    fun celebrationDistance(pos: Int): Int {
        if (celebrationOrigin !in 0 until 81 || pos !in celebrationCells) return -1
        return kotlin.math.abs(pos / 9 - celebrationOrigin / 9) +
            kotlin.math.abs(pos % 9 - celebrationOrigin % 9)
    }

    fun celebrationMaxDistance(): Int =
        celebrationCells.maxOfOrNull(::celebrationDistance)?.coerceAtLeast(0) ?: 0

    fun advanceCelebration(id: Int, step: Int) {
        if (celebrationId == id) celebrationStep = step
    }

    fun clearCelebration(id: Int) {
        if (celebrationId == id) {
            celebrationCells.clear()
            celebrationOrigin = -1
            celebrationStep = -1
        }
    }

    private fun selectNextIncompleteNumber(completedNumber: Int) {
        for (step in 1..8) {
            val nextNumber = ((completedNumber - 1 + step) % 9) + 1
            if (placedCount(nextNumber) < 9) {
                val nextPosition = board.indexOfFirst { it == nextNumber }
                if (nextPosition >= 0) selected = nextPosition
                return
            }
        }
    }

    fun erase() {
        if (won || failed()) return
        val pos = selected
        if (pos == -1 || given[pos]) return
        pushHistory(pos)
        board[pos] = 0
        notes[pos].clear()
    }

    fun undo() {
        if (history.isEmpty() || failed()) return
        val last = history.removeLast()
        board[last.pos] = last.value
        notes[last.pos].clear(); notes[last.pos].addAll(last.notes)
        won = false
    }

    fun toggleNotes() { if (!failed()) notesMode = !notesMode }

    fun hint() {
        if (won || failed()) return
        hintsUsed++
        val candidates = (0 until 81).filter { !given[it] && board[it] != solution[it] }
        if (candidates.isEmpty()) return
        val pos = if (settings.smartHints && selected in candidates) selected else candidates.random()
        pushHistory(pos)
        board[pos] = solution[pos]
        notes[pos].clear()
        removeNoteFromBox(pos, solution[pos])
        selected = pos
        checkWin()
    }

    fun completeLastCell() {
        if (won || failed() || remaining() != 1) return
        autoCompleted = true
        val pos = board.indexOfFirst { it == 0 }
        if (pos >= 0) {
            pushHistory(pos)
            board[pos] = solution[pos]
            notes[pos].clear()
            removeNoteFromBox(pos, solution[pos])
            selected = pos
            checkWin()
        }
    }

    private fun checkWin() {
        won = (0 until 81).all { board[it] == solution[it] }
    }

    fun remaining(): Int = board.count { it == 0 }
    fun placedCount(n: Int): Int = board.count { it == n }
    fun failed(): Boolean = settings.errorLimit && mistakes >= 3 && !won
    fun errorLabel(): String = if (settings.errorLimit) "$mistakes/3" else "$mistakes"

    fun score(final: Boolean = won): Int {
        val base = intArrayOf(1000, 2000, 3500, 5000, 7500, 10000)[difficulty.ordinal]
        val editable = given.count { !it }.coerceAtLeast(1)
        val correct = (0 until 81).count { !given[it] && board[it] == solution[it] }
        var points = if (final) base else base * correct / editable
        points -= mistakes * 100 + hintsUsed * 200
        if (final) {
            if (mistakes == 0) points += base * 20 / 100
            if (hintsUsed == 0) points += base * 10 / 100
            if (!autoCompleted) {
                val target = intArrayOf(600, 900, 1200, 1500, 1800, 2100)[difficulty.ordinal]
                points += base * (target - seconds).coerceAtLeast(0) / target / 2
            }
        }
        return points.coerceAtLeast(0)
    }
}

private data class LevelStats(
    val started: Int, val completed: Int, val abandoned: Int,
    val totalSeconds: Long, val bestSeconds: Int, val bestScore: Int, val flawless: Int
)

private data class ChallengeResult(
    val code: String,
    val seconds: Int,
    val score: Int,
    val mistakes: Int,
    val attempts: Int,
    val completedAt: Long
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sudoku_settings", Context.MODE_PRIVATE)

    var animations by mutableStateOf(prefs.getBoolean("animations", true))
        private set
    var smartHints by mutableStateOf(prefs.getBoolean("smart_hints", true))
        private set
    var errorLimit by mutableStateOf(prefs.getBoolean("error_limit", true))
        private set

    fun updateAnimations(value: Boolean) {
        animations = value
        prefs.edit().putBoolean("animations", value).apply()
    }

    fun updateSmartHints(value: Boolean) {
        smartHints = value
        prefs.edit().putBoolean("smart_hints", value).apply()
    }

    fun updateErrorLimit(value: Boolean) {
        errorLimit = value
        prefs.edit().putBoolean("error_limit", value).apply()
    }
}

private class StatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sudoku_stats", Context.MODE_PRIVATE)
    private var activeLevel: SudokuEngine.Difficulty? = null
    private fun key(level: SudokuEngine.Difficulty, field: String) = "${level.name}_$field"
    private fun int(level: SudokuEngine.Difficulty, field: String) = prefs.getInt(key(level, field), 0)

    fun stats(level: SudokuEngine.Difficulty) = LevelStats(
        int(level, "started"), int(level, "completed"), int(level, "abandoned"),
        prefs.getLong(key(level, "totalSeconds"), 0L), int(level, "bestSeconds"),
        int(level, "bestScore"), int(level, "flawless")
    )

    fun isUnlocked(level: SudokuEngine.Difficulty): Boolean =
        level.ordinal < 2 || stats(SudokuEngine.Difficulty.values()[level.ordinal - 1]).completed >= 5

    fun unlockProgress(level: SudokuEngine.Difficulty): Int = if (level.ordinal < 2) 5 else
        stats(SudokuEngine.Difficulty.values()[level.ordinal - 1]).completed.coerceAtMost(5)

    fun unlockedLevels(): List<SudokuEngine.Difficulty> =
        SudokuEngine.Difficulty.values().filter(::isUnlocked)

    fun dailyCode(date: LocalDate): String {
        val key = "daily_code_${date}"
        prefs.getString(key, null)?.let { return ChallengeCodes.normalize(it) }
        return ChallengeCodes.daily(date, unlockedLevels()).also { code ->
            prefs.edit().putString(key, code).apply()
        }
    }

    fun start(level: SudokuEngine.Difficulty) {
        abandonActive()
        activeLevel = level
        prefs.edit().putInt(key(level, "started"), int(level, "started") + 1).apply()
    }

    fun abandonActive() {
        val level = activeLevel ?: return
        prefs.edit().putInt(key(level, "abandoned"), int(level, "abandoned") + 1).apply()
        activeLevel = null
        updateStreak(false)
    }

    fun complete(game: GameState) {
        val level = activeLevel ?: return
        val old = stats(level)
        val bestTime = if (old.bestSeconds == 0) game.seconds else minOf(old.bestSeconds, game.seconds)
        prefs.edit().putInt(key(level, "completed"), old.completed + 1)
            .putLong(key(level, "totalSeconds"), old.totalSeconds + game.seconds)
            .putInt(key(level, "bestSeconds"), bestTime)
            .putInt(key(level, "bestScore"), maxOf(old.bestScore, game.score(true)))
            .putInt(key(level, "flawless"), old.flawless + if (game.mistakes == 0) 1 else 0).apply()
        saveChallengeResult(game)
        activeLevel = null
        updateStreak(true)
    }

    private fun challengeKey(code: String, field: String) = "challenge_${ChallengeCodes.normalize(code)}_$field"

    private fun saveChallengeResult(game: GameState) {
        val code = ChallengeCodes.normalize(game.gameCode)
        val previous = challengeResult(code)
        val codes = prefs.getStringSet("challenge_codes", emptySet()).orEmpty().toMutableSet()
        codes.add(code)
        prefs.edit()
            .putStringSet("challenge_codes", codes)
            .putInt(challengeKey(code, "seconds"), if (previous == null) game.seconds else minOf(previous.seconds, game.seconds))
            .putInt(challengeKey(code, "score"), maxOf(previous?.score ?: 0, game.score(true)))
            .putInt(challengeKey(code, "mistakes"), if (previous == null || game.seconds <= previous.seconds) game.mistakes else previous.mistakes)
            .putInt(challengeKey(code, "attempts"), (previous?.attempts ?: 0) + 1)
            .putLong(challengeKey(code, "completedAt"), System.currentTimeMillis())
            .apply()
    }

    fun challengeResult(code: String): ChallengeResult? {
        val normalized = ChallengeCodes.normalize(code)
        if (!prefs.getStringSet("challenge_codes", emptySet()).orEmpty().contains(normalized)) return null
        return ChallengeResult(
            normalized,
            prefs.getInt(challengeKey(normalized, "seconds"), 0),
            prefs.getInt(challengeKey(normalized, "score"), 0),
            prefs.getInt(challengeKey(normalized, "mistakes"), 0),
            prefs.getInt(challengeKey(normalized, "attempts"), 1),
            prefs.getLong(challengeKey(normalized, "completedAt"), 0L)
        )
    }

    fun challengeHistory(): List<ChallengeResult> =
        prefs.getStringSet("challenge_codes", emptySet()).orEmpty()
            .mapNotNull(::challengeResult)
            .sortedByDescending { it.completedAt }

    private fun updateStreak(won: Boolean) {
        val current = if (won) prefs.getInt("currentStreak", 0) + 1 else 0
        prefs.edit().putInt("currentStreak", current)
            .putInt("bestStreak", maxOf(prefs.getInt("bestStreak", 0), current)).apply()
    }

    fun currentStreak() = prefs.getInt("currentStreak", 0)
    fun bestStreak() = prefs.getInt("bestStreak", 0)
}

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
    var statsVersion by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var showLevels by remember { mutableStateOf(false) }
    var showDailyResumeDialog by remember { mutableStateOf(false) }
    var pendingDailyDate by remember { mutableStateOf<LocalDate?>(null) }
    var dailyGameDate by remember { mutableStateOf<LocalDate?>(null) }
    var dailySelectFirstAvailable by remember { mutableStateOf(false) }
    var showDailyResultDialog by remember { mutableStateOf(false) }
    var dailyResultSeconds by remember { mutableStateOf(0) }
    var dailyResultScore by remember { mutableStateOf(0) }
    val game = remember { GameState(SudokuEngine.Difficulty.MEDIO, settings) }

    LaunchedEffect(game.won, game.generation) {
        if (game.won) {
            stats.complete(game)
            statsVersion++
            if (dailyGameDate != null) {
                dailyResultSeconds = game.seconds
                dailyResultScore = game.score(true)
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
                if (dailyGameDate != null && !game.won) {
                    // La sfida resta in memoria: il pannello verrà mostrato
                    // soltanto quando l'utente selezionerà di nuovo quel giorno.
                    screen = AppScreen.DAILY
                } else {
                    if (!game.won) stats.abandonActive()
                    statsVersion++
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

    if (showLevels) {
        DifficultyDialog(
            stats = stats,
            statsVersion = statsVersion,
            onDismiss = { showLevels = false },
            onSelected = {
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
}

@Composable
private fun HomeScreen(
    onPlay: () -> Unit,
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

        Spacer(Modifier.height(24.dp))
        val today = LocalDate.now()
        val monthNames = listOf(
            "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
            "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(104.dp)
                .background(Color(0xFFEDF5FD), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFD1E0F0), RoundedCornerShape(16.dp))
                .clickable { onDaily() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("▦", color = AppBlue, fontSize = 45.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Sfida del giorno", color = Color(0xFF1D2738), fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${today.dayOfMonth} ${monthNames[today.monthValue - 1]}", color = AppBlue, fontSize = 16.sp)
            }
            Box(Modifier.width(1.dp).height(62.dp).background(Color(0xFFD2DDEB)))
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
                                            if (!completed && !isFuture) Modifier.clickable { selectedDate = month.atDay(day) }
                                            else Modifier
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

@Composable
fun SudokuScreen(
    game: GameState,
    statsVersion: Int,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onChangeLevel: () -> Unit
) {
    statsVersion
    BackHandler(onBack = onBack)

    // timer: riparte a ogni nuova partita, si ferma automaticamente a vittoria
    LaunchedEffect(game.generation, game.won) {
        while (isActive && !game.won && !game.failed()) {
            delay(1000)
            if (!game.paused) game.seconds++
        }
    }

    LaunchedEffect(game.celebrationId) {
        val id = game.celebrationId
        if (id > 0) {
            val maxDistance = game.celebrationMaxDistance()
            for (step in 0..maxDistance) {
                game.advanceCelebration(id, step)
                delay(85)
            }
            delay(280)
            game.clearCelebration(id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ModernTopBar(game, onBack, onSettings)
            Text(
                "CODICE  ${game.gameCode}",
                color = AppBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(AppBlueSoft, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 5.dp)
            )
            Spacer(Modifier.height(6.dp))
            ModernStats(game)
            Spacer(Modifier.height(8.dp))
            Board(game)
            Spacer(Modifier.height(22.dp))
            ModernActions(game)
            Spacer(Modifier.height(16.dp))
            NumberPad(game)
        }

        if (game.paused) PauseOverlay(game)
        if (game.won) WinOverlay(game, onMenu = onBack, onChangeLevel = onChangeLevel)
        if (game.failed()) FailureOverlay(game)
    }
}

@Composable
private fun ModernTopBar(game: GameState, onBack: () -> Unit, onSettings: () -> Unit) {
    val m = (game.seconds / 60).toString().padStart(2, '0')
    val s = (game.seconds % 60).toString().padStart(2, '0')
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("‹", color = AppBlue, fontSize = 48.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { onBack() })
        Text("$m:$s", color = Color(0xFF263A58), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("⟳", color = AppBlue, fontSize = 38.sp, modifier = Modifier.clickable { game.reset() })
            Text(if (game.paused) "▶" else "Ⅱ", color = AppBlue, fontSize = 30.sp,
                modifier = Modifier.clickable { game.paused = !game.paused })
            Text("⚙", color = AppBlue, fontSize = 34.sp, modifier = Modifier.clickable { onSettings() })
        }
    }
}

@Composable
private fun ModernStats(game: GameState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        ModernStat("Oggi", "★ 0")
        ModernStat("Difficoltà", game.difficulty.label.lowercase().replaceFirstChar { it.uppercase() })
        ModernStat("Punteggio", "${game.score()}")
        ModernStat("Errori", game.errorLabel())
    }
}

@Composable
private fun ModernStat(label: String, value: String, clickable: Boolean = false, onClick: () -> Unit = {}) {
    Column(
        modifier = if (clickable) Modifier.clickable { onClick() }.padding(4.dp) else Modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Board(game: GameState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.White)
            .border(2.dp, GridLine)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(9),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize().background(Color.White)
        ) {
            items(81) { pos -> SudokuCell(game, pos) }
        }
    }
}

@Composable
private fun SudokuCell(game: GameState, pos: Int) {
    val r = pos / 9
    val c = pos % 9
    val b = (r / 3) * 3 + (c / 3)
    val value = game.board.getOrElse(pos) { 0 }
    val given = game.given[pos]
    val selPos = game.selected
    val selVal = if (selPos != -1) game.board.getOrElse(selPos) { 0 } else 0
    val selRow = if (selPos != -1) selPos / 9 else -1
    val selCol = if (selPos != -1) selPos % 9 else -1
    val selBox = if (selPos != -1) (selRow / 3) * 3 + (selCol / 3) else -1

    val isSelected = pos == selPos
    val isPeer = selPos != -1 && pos != selPos && (r == selRow || c == selCol || b == selBox)
    val isSameNum = selVal != 0 && value == selVal && pos != selPos
    val isError = value != 0 && value != game.solution[pos]
    val celebrationDistance = game.celebrationDistance(pos)
    val isCelebrationWave = celebrationDistance >= 0 && celebrationDistance == game.celebrationStep
    val isCelebrationTrail = celebrationDistance in 0 until game.celebrationStep

    val bg = when {
        isError && isSelected -> Color(0xFFFFB9B9)
        isCelebrationWave -> AppBlue
        isCelebrationTrail -> Color(0xFF79B8F3)
        isSelected -> Color(0xFF79B8F3)
        isError -> Color(0xFFFFE1E1)
        isSameNum -> Color(0xFFD8CCF4)
        isPeer -> Color(0xFFE8EEF5)
        else -> Color.White
    }
    val textColor = when {
        isError -> Color(0xFFD32F2F)
        isCelebrationWave -> Color.White
        given -> Color.Black
        value != 0 -> AppBlue
        else -> Color.Black
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(bg)
            .border(
                width = 0.5.dp,
                color = CellLine
            )
            .then(
                Modifier
                    .thickEdge(right = c == 2 || c == 5, bottom = r == 2 || r == 5)
            )
            .clickable { game.select(pos) },
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) {
            Text(
                "$value",
                fontWeight = if (given) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 23.sp,
                color = textColor
            )
        } else if (game.notes[pos].isNotEmpty()) {
            NotesGrid(game.notes[pos])
        }
    }
}

@Composable
private fun NotesGrid(activeNotes: List<Int>) {
    Column(Modifier.fillMaxSize()) {
        for (row in 0..2) {
            Row(Modifier.weight(1f)) {
                for (col in 0..2) {
                    val n = row * 3 + col + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (activeNotes.contains(n)) {
                            Text(
                                "$n",
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF405D7C)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.thickEdge(right: Boolean, bottom: Boolean): Modifier {
    var m = this
    if (right) m = m.drawBehind {
        val stroke = 3.dp.toPx()
        drawLine(
            color = GridLine,
            start = androidx.compose.ui.geometry.Offset(size.width - stroke / 2f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width - stroke / 2f, size.height),
            strokeWidth = stroke
        )
    }
    if (bottom) m = m.drawBehind {
        val stroke = 3.dp.toPx()
        drawLine(
            color = GridLine,
            start = androidx.compose.ui.geometry.Offset(0f, size.height - stroke / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height - stroke / 2f),
            strokeWidth = stroke
        )
    }
    return m
}

@Composable
private fun ModernActions(game: GameState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        ModernAction("↶", "Annulla") { game.undo() }
        EraserAction { game.erase() }
        ModernAction(if (game.notesMode) "✎ ON" else "✎", "Note", game.notesMode) { game.toggleNotes() }
        ModernAction("♧", "Suggerim.") { game.hint() }
    }
}

@Composable
private fun EraserAction(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(82.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.padding(top = 7.dp, bottom = 8.dp).size(width = 31.dp, height = 19.dp)
                .rotate(-35f).border(3.dp, AppText, RoundedCornerShape(4.dp))
        )
        Text("Cancella", color = AppText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModernAction(icon: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(82.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, color = if (active) AppBlue else AppText, fontSize = 31.sp)
        Text(label, color = AppText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NumberPad(game: GameState) {
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        for (n in 1..9) {
            val left = 9 - game.placedCount(n)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (left > 0) Modifier.clickable { game.input(n) } else Modifier)
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (left > 0) {
                    Text("$n", fontWeight = FontWeight.Normal, fontSize = 34.sp, color = AppBlue)
                    Text("$left", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF9AA3B2))
                } else {
                    Text("✓", fontWeight = FontWeight.Bold, fontSize = 29.sp, color = AppBlue)
                    Text(" ", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(game: GameState) {
    Box(
        Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.94f)).clickable { game.paused = false },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ⅱ", color = AppBlue, fontSize = 64.sp)
            Text("Partita in pausa", color = Color(0xFF263A58), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Tocca per continuare", color = AppText, fontSize = 16.sp)
        }
    }
}

@Composable
private fun FailureOverlay(game: GameState) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 30.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(26.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Hai perso", color = Color(0xFF202332), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                "Hai perso la partita perché hai commesso 3 errori",
                color = Color(0xFF303442), fontSize = 19.sp, lineHeight = 27.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { game.retry() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
            ) { Text("Riprova", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { game.reset() },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Cambia schema", color = AppBlue, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun WinOverlay(game: GameState, onMenu: () -> Unit, onChangeLevel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✓", color = AppBlue, fontSize = 68.sp)
            Text("Sudoku completato!", color = Color(0xFF263A58), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Livello ${game.difficulty.label.lowercase().replaceFirstChar { it.uppercase() }}", color = AppText, fontSize = 17.sp)
            Text("Codice ${game.gameCode}", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Punteggio ${game.score(true)}", color = AppBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { game.reset() },
                modifier = Modifier.width(230.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
            ) { Text("Gioca ancora") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onChangeLevel, modifier = Modifier.width(230.dp)) { Text("Cambia livello", color = AppBlue) }
            TextButton(onClick = onMenu, modifier = Modifier.width(230.dp)) { Text("Torna al menu", color = AppText) }
        }
    }
}
