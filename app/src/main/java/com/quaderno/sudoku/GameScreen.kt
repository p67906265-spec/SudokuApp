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
                delay(45)
            }
            delay(150)
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
        if (game.won) {
            ConfettiOverlay()
            WinOverlay(game, onMenu = onBack, onChangeLevel = onChangeLevel)
        }
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
internal fun NotesGrid(activeNotes: List<Int>) {
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

internal fun Modifier.thickEdge(right: Boolean, bottom: Boolean): Modifier {
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
        ModernAction("♧", "Aiuti: ${game.hintsRemaining()}") { game.hint() }
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
private fun ConfettiOverlay() {
    val pieces = remember { List(42) { i -> Triple((i * 37 % 100) / 100f, (i * 53 % 100) / 100f, i % 4) } }
    Box(Modifier.fillMaxSize()) {
        pieces.forEachIndexed { i, p ->
            val color = listOf(AppBlue, Color(0xFFFFC32D), Color(0xFF46B7EB), Color(0xFFE86A92))[p.third]
            Box(
                Modifier
                    .offset(x = (p.first * 360).dp, y = (p.second * 620).dp)
                    .size(if (i % 2 == 0) 8.dp else 6.dp, 13.dp)
                    .rotate((i * 29).toFloat())
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun WinOverlay(game: GameState, onMenu: () -> Unit, onChangeLevel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(start = 24.dp, top = 58.dp, end = 24.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Complimenti!",
                        color = Color(0xFF263A58),
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Livello ${game.difficulty.label.lowercase().replaceFirstChar { it.uppercase() }}",
                        color = AppText,
                        fontSize = 18.sp
                    )
                    Text(
                        "Codice ${game.gameCode}",
                        color = AppText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Punteggio ${game.score(true)}",
                        color = AppBlue,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { game.reset() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                    ) {
                        Text("Gioca ancora", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onChangeLevel,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppBlue)
                    ) {
                        Text("Cambia livello", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onMenu,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppBlueSoft,
                            contentColor = AppBlue
                        )
                    ) {
                        Text("Torna al menu", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Surface(
                modifier = Modifier.size(82.dp),
                shape = CircleShape,
                color = AppBlue,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 51.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
