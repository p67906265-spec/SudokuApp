package com.quaderno.sudoku

import kotlin.random.Random

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

internal object ChallengeCodes {
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
