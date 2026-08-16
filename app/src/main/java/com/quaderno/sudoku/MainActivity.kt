package com.quaderno.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Palette e tipografia "quaderno a matita"
// ---------------------------------------------------------------------------
private val Paper = Color(0xFFEDE7D9)
private val Paper2 = Color(0xFFE4DCC8)
private val Ink = Color(0xFF23241F)
private val InkSoft = Color(0xFF5B5C52)
private val LineStrong = Color(0xFF23241F)
private val Teal = Color(0xFF2F6F63)
private val TealSoft = Color(0xFFD8E7E1)
private val Rust = Color(0xFFA64B34)
private val RustSoft = Color(0xFFF1DCD3)
private val GoldSoft = Color(0xFFF0E3C4)
private val Gold = Color(0xFFB7862C)

private val Display = FontFamily.Serif
private val Mono = FontFamily.Monospace

// ---------------------------------------------------------------------------
// Motore Sudoku: generazione griglia piena, rimozione celle con verifica
// di soluzione unica (backtracking con bitmask, stessa logica per ogni livello)
// ---------------------------------------------------------------------------
object SudokuEngine {

    enum class Difficulty(val label: String, val clues: Int) {
        FACILE("FACILE", 40),
        MEDIO("MEDIO", 33),
        DIFFICILE("DIFFICILE", 27),
        ESPERTO("ESPERTO", 23)
    }

    data class Puzzle(val given: IntArray, val solution: IntArray)

    private fun boxOf(r: Int, c: Int) = (r / 3) * 3 + (c / 3)

    private fun generateFullGrid(): IntArray {
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
            val nums = (1..9).shuffled(Random)
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

    fun generatePuzzle(difficulty: Difficulty): Puzzle {
        val full = generateFullGrid()
        val puzzle = full.copyOf()
        val target = difficulty.clues
        val order = (0 until 81).shuffled(Random)
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

// ---------------------------------------------------------------------------
// Stato di partita
// ---------------------------------------------------------------------------
class GameState(difficulty: SudokuEngine.Difficulty) {
    var difficulty by mutableStateOf(difficulty)
    var given: BooleanArray = BooleanArray(81)
    var solution: IntArray = IntArray(81)
    val board: SnapshotStateList<Int> = mutableStateListOf()
    val notes: List<SnapshotStateList<Int>> = List(81) { mutableStateListOf() }
    var selected by mutableStateOf(-1)
    var notesMode by mutableStateOf(false)
    var mistakes by mutableStateOf(0)
    var seconds by mutableStateOf(0)
    var won by mutableStateOf(false)
    var generation by mutableStateOf(0)
    private val history = ArrayDeque<HistoryEntry>()

    private data class HistoryEntry(val pos: Int, val value: Int, val notes: List<Int>)

    init { reset(difficulty) }

    fun reset(newDifficulty: SudokuEngine.Difficulty = difficulty) {
        difficulty = newDifficulty
        val puzzle = SudokuEngine.generatePuzzle(newDifficulty)
        given = BooleanArray(81) { puzzle.given[it] != 0 }
        solution = puzzle.solution
        board.clear(); board.addAll(puzzle.given.toList())
        notes.forEach { it.clear() }
        selected = -1
        notesMode = false
        mistakes = 0
        seconds = 0
        won = false
        generation++
        history.clear()
    }

    private fun pushHistory(pos: Int) {
        history.addLast(HistoryEntry(pos, board[pos], notes[pos].toList()))
        if (history.size > 200) history.removeFirst()
    }

    fun select(pos: Int) { if (!won) selected = pos }

    fun input(n: Int) {
        if (won) return
        val pos = selected
        if (pos == -1 || given[pos]) return

        if (notesMode) {
            if (board[pos] != 0) return
            pushHistory(pos)
            if (notes[pos].contains(n)) notes[pos].remove(n) else notes[pos].add(n)
            return
        }

        pushHistory(pos)
        if (board[pos] == n) {
            board[pos] = 0
        } else {
            board[pos] = n
            notes[pos].clear()
            if (solution[pos] != n) mistakes++
        }
        checkWin()
    }

    fun erase() {
        if (won) return
        val pos = selected
        if (pos == -1 || given[pos]) return
        pushHistory(pos)
        board[pos] = 0
        notes[pos].clear()
    }

    fun undo() {
        if (history.isEmpty()) return
        val last = history.removeLast()
        board[last.pos] = last.value
        notes[last.pos].clear(); notes[last.pos].addAll(last.notes)
        won = false
    }

    fun toggleNotes() { notesMode = !notesMode }

    fun hint() {
        if (won) return
        var pos = selected
        if (pos == -1 || given[pos] || board[pos] == solution[pos]) {
            val candidates = (0 until 81).filter { !given[it] && board[it] != solution[it] }
            if (candidates.isEmpty()) return
            pos = candidates.random()
        }
        pushHistory(pos)
        board[pos] = solution[pos]
        notes[pos].clear()
        selected = pos
        checkWin()
    }

    private fun checkWin() {
        won = (0 until 81).all { board[it] == solution[it] }
    }

    fun remaining(): Int = board.count { it == 0 }
    fun placedCount(n: Int): Int = board.count { it == n }
}

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Paper) {
                    SudokuScreen()
                }
            }
        }
    }
}

@Composable
fun SudokuScreen() {
    val game = remember { GameState(SudokuEngine.Difficulty.MEDIO) }

    // timer: riparte a ogni nuova partita, si ferma automaticamente a vittoria
    LaunchedEffect(game.generation, game.won) {
        while (isActive && !game.won) {
            delay(1000)
            game.seconds++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Header()
        Spacer(Modifier.height(14.dp))
        DifficultyBar(game)
        Spacer(Modifier.height(14.dp))
        StatRow(game)
        Spacer(Modifier.height(10.dp))
        Board(game)
        if (game.won) {
            Spacer(Modifier.height(14.dp))
            WinBanner(game)
        }
        Spacer(Modifier.height(16.dp))
        ActionRow(game)
        Spacer(Modifier.height(10.dp))
        NumberPad(game)
        Spacer(Modifier.height(14.dp))
        Text(
            "Tocca una casella, poi un numero. Le note segnano le ipotesi a matita.",
            fontFamily = Mono,
            fontSize = 10.sp,
            color = InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            "QUADERNO N.7",
            fontFamily = Mono,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = InkSoft
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Sudoku ", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Ink)
            Text("a matita", fontFamily = Display, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 26.sp, color = Rust)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(LineStrong))
    }
}

@Composable
private fun DifficultyBar(game: GameState) {
    val levels = SudokuEngine.Difficulty.values()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, LineStrong, RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
    ) {
        levels.forEachIndexed { i, d ->
            val active = game.difficulty == d
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) Ink else Color.Transparent)
                    .clickable { game.reset(d) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    d.label,
                    fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (active) Paper else Ink
                )
            }
            if (i < levels.size - 1) {
                Box(
                    Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(LineStrong)
                )
            }
        }
    }
}

@Composable
private fun StatRow(game: GameState) {
    val m = (game.seconds / 60).toString().padStart(2, '0')
    val s = (game.seconds % 60).toString().padStart(2, '0')
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        StatItem("Tempo", "$m:$s")
        StatItem("Errori", "${game.mistakes}")
        StatItem("Vuote", "${game.remaining()}")
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Row {
        Text("$label ", fontFamily = Mono, fontSize = 12.sp, color = InkSoft)
        Text(value, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
private fun Board(game: GameState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Paper2)
            .border(2.dp, LineStrong)
            .padding(6.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(9),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize().background(LineStrong).border(1.dp, LineStrong)
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

    val bg = when {
        isError && isSelected -> RustSoft
        isSelected -> TealSoft
        isError -> RustSoft
        isSameNum -> GoldSoft
        isPeer -> Paper2
        else -> Paper
    }
    val textColor = when {
        isError -> Rust
        given -> Ink
        value != 0 -> Teal
        else -> Ink
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(bg)
            .border(
                width = 0.5.dp,
                color = LineStrong.copy(alpha = 0.35f)
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
                fontFamily = Display,
                fontWeight = if (given) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 20.sp,
                color = textColor
            )
        } else if (game.notes[pos].isNotEmpty()) {
            NotesGrid(game.notes[pos])
        }
    }
}

@Composable
private fun NotesGrid(activeNotes: List<Int>) {
    Column(Modifier.fillMaxSize().padding(2.dp)) {
        for (row in 0..2) {
            Row(Modifier.weight(1f)) {
                for (col in 0..2) {
                    val n = row * 3 + col + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (activeNotes.contains(n)) {
                            Text("$n", fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
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
        drawLine(
            color = LineStrong,
            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = 3f
        )
    }
    if (bottom) m = m.drawBehind {
        drawLine(
            color = LineStrong,
            start = androidx.compose.ui.geometry.Offset(0f, size.height),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = 3f
        )
    }
    return m
}

@Composable
private fun WinBanner(game: GameState) {
    val m = (game.seconds / 60).toString().padStart(2, '0')
    val s = (game.seconds % 60).toString().padStart(2, '0')
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Teal, RoundedCornerShape(3.dp))
            .background(TealSoft, RoundedCornerShape(3.dp))
            .padding(14.dp)
    ) {
        Text("Griglia completata.", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Teal)
        Text(
            "Livello ${game.difficulty.label.lowercase()} · ${game.mistakes} errori · $m:$s",
            fontFamily = Mono, fontSize = 11.sp, color = InkSoft
        )
    }
}

@Composable
private fun ActionRow(game: GameState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ActionButton("↻", "Nuova", Modifier.weight(1f)) { game.reset() }
        ActionButton("←", "Annulla", Modifier.weight(1f)) { game.undo() }
        ActionButton("×", "Cancella", Modifier.weight(1f)) { game.erase() }
        ActionButton("✎", "Note", Modifier.weight(1f), active = game.notesMode) { game.toggleNotes() }
        ActionButton("☉", "Aiuto", Modifier.weight(1f)) { game.hint() }
    }
}

@Composable
private fun ActionButton(icon: String, label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .border(1.5.dp, if (active) Gold else LineStrong, RoundedCornerShape(3.dp))
            .background(if (active) GoldSoft else Paper2, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(2.dp))
        Text(label, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun NumberPad(game: GameState) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        for (n in 1..9) {
            val left = 9 - game.placedCount(n)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.5.dp, LineStrong, RoundedCornerShape(3.dp))
                    .background(Paper, RoundedCornerShape(3.dp))
                    .then(if (left > 0) Modifier.clickable { game.input(n) } else Modifier)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$n",
                    fontFamily = Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = if (left > 0) Ink else Ink.copy(alpha = 0.25f)
                )
                Text(
                    if (left > 0) "$left" else "✓",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = InkSoft
                )
            }
        }
    }
}
