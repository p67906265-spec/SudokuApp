package com.quaderno.sudoku

import android.os.Bundle
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
    var showWinSummary by remember(game.generation) { mutableStateOf(false) }
    LaunchedEffect(game.won, game.generation) {
        showWinSummary = false
        if (game.won) {
            delay(3500)
            showWinSummary = true
        }
    }

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
            FireworksOverlay()
            if (showWinSummary) {
                WinOverlay(game, onMenu = onBack, onChangeLevel = onChangeLevel)
            }
        }
        if (game.failed()) FailureOverlay(game, onExit = onBack)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ColorAction("↶", "Annulla", Color(0xFFFF9F43)) { game.undo() }
        ColorAction("▱", "Cancella", Color(0xFFFF5576)) { game.erase() }
        ColorAction(if (game.notesMode) "✎" else "✎", "Note", Color(0xFF4B94F2), game.notesMode) { game.toggleNotes() }
        ColorAction("♣", "Aiuti: ${game.hintsRemaining()}", Color(0xFF42C49A)) { game.hint() }
    }
}

@Composable
private fun ColorAction(icon: String, label: String, color: Color, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(86.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (active) color.copy(alpha = 0.82f) else color, CircleShape)
                .border(3.dp, color.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = AppText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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
private fun FailureOverlay(game: GameState, onExit: () -> Unit) {
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
            TextButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Esci", color = AppText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun FireworksOverlay() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 3400, easing = LinearEasing))
    }
    val colors = listOf(Color(0xFFFFC928), Color(0xFFFF5A8A), Color(0xFF4EA5FF), Color(0xFF54D67A), Color(0xFFFF8A3D), Color(0xFFB86CFF), Color.White)
    val bursts = remember { listOf(
        Triple(-130f,-250f,0.00f), Triple(120f,-225f,0.06f), Triple(-25f,-155f,0.13f),
        Triple(145f,-75f,0.20f), Triple(-145f,-30f,0.27f), Triple(45f,30f,0.34f),
        Triple(145f,100f,0.41f), Triple(-95f,145f,0.48f), Triple(20f,210f,0.55f),
        Triple(-145f,270f,0.62f), Triple(130f,295f,0.68f)
    ) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f))) {
        bursts.forEachIndexed { bi, b ->
            val local = ((progress.value-b.third)/0.30f).coerceIn(0f,1f)
            if (local > 0f && local < 1f) repeat(72) { i ->
                val a=(i*(360f/72f)+bi*11f)*(Math.PI/180.0)
                val d=(105f+(i%9)*15f)*local
                val fade=(1f-local).coerceIn(0f,1f)
                val dot=if(i%8==0) 10.dp else if(i%3==0) 7.dp else 5.dp
                Box(Modifier.align(Alignment.Center).graphicsLayer {
                    translationX=b.first+kotlin.math.cos(a).toFloat()*d
                    translationY=b.second+kotlin.math.sin(a).toFloat()*d+28f*local*local
                    alpha=fade; scaleX=1.15f-local*0.25f; scaleY=scaleX
                }.size(dot).background(colors[(i+bi)%colors.size], CircleShape))
            }
        }
    }
}

@Composable
private fun WinOverlay(game: GameState, onMenu: () -> Unit, onChangeLevel: () -> Unit) {
    val m=(game.seconds/60).toString().padStart(2,'0'); val sec=(game.seconds%60).toString().padStart(2,'0')
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.58f)), contentAlignment=Alignment.Center) {
        Surface(Modifier.fillMaxWidth().padding(horizontal=28.dp), shape=RoundedCornerShape(24.dp), color=Color(0xFF071B2C), shadowElevation=14.dp, border=androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF168BFF))) {
            Column(Modifier.padding(24.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                Text("CONGRATULAZIONI! ✨", color=Color(0xFFFFC83D), fontSize=25.sp, fontWeight=FontWeight.Bold)
                Text("Hai completato il Sudoku!", color=Color.White, fontSize=16.sp)
                Spacer(Modifier.height(10.dp)); Text("🏆", fontSize=52.sp); Spacer(Modifier.height(8.dp))
                SummaryRow("◷  Tempo", "$m:$sec", Color(0xFF3EA2FF))
                SummaryRow("★  Punteggio", "${game.score(true)}", Color(0xFF45D66F))
                SummaryRow("◎  Errori", "${game.mistakes}", Color(0xFFFF4D55))
                SummaryRow("▥  Difficoltà", game.difficulty.label, Color(0xFFD65CFF))
                Spacer(Modifier.height(18.dp))
                Button({game.reset()}, Modifier.fillMaxWidth().height(52.dp), shape=RoundedCornerShape(14.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF1477E8))) { Text("▶  Nuovo Gioco", fontWeight=FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onChangeLevel, Modifier.weight(1f), border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF168BFF))) { Text("Livello", color=Color.White) }
                    OutlinedButton(onMenu, Modifier.weight(1f), border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF168BFF))) { Text("Menu", color=Color.White) }
                }
            }
        }
    }
}

@Composable private fun SummaryRow(label:String, value:String, valueColor:Color) {
    Row(Modifier.fillMaxWidth().padding(vertical=7.dp), horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold); Text(value,color=valueColor,fontSize=16.sp,fontWeight=FontWeight.Bold)
    }
}
