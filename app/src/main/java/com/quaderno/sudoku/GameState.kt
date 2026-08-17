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

    private fun isInSameBox(origin: Int, candidate: Int): Boolean =
        origin / 9 / 3 == candidate / 9 / 3 && origin % 9 / 3 == candidate % 9 / 3

    private fun removeNoteFromPeers(pos: Int, n: Int) {
        val row = pos / 9
        val col = pos % 9
        (0 until 81).forEach { candidate ->
            val sameRow = candidate / 9 == row
            val sameColumn = candidate % 9 == col
            if (sameRow || sameColumn || isInSameBox(pos, candidate)) {
                notes[candidate].remove(n)
            }
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
            removeNoteFromPeers(pos, n)
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
        if (won || failed() || hintsUsed >= 2) return
        val candidates = (0 until 81).filter { !given[it] && board[it] != solution[it] }
        if (candidates.isEmpty()) return
        hintsUsed++
        val pos = if (settings.smartHints && selected in candidates) selected else candidates.random()
        pushHistory(pos)
        board[pos] = solution[pos]
        notes[pos].clear()
        removeNoteFromPeers(pos, solution[pos])
        selected = pos
        checkWin()
    }

    fun hintsRemaining(): Int = (2 - hintsUsed).coerceAtLeast(0)

    fun completeLastCell() {
        if (won || failed() || remaining() != 1) return
        autoCompleted = true
        val pos = board.indexOfFirst { it == 0 }
        if (pos >= 0) {
            pushHistory(pos)
            board[pos] = solution[pos]
            notes[pos].clear()
            removeNoteFromPeers(pos, solution[pos])
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
