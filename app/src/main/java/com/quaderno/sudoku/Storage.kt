package com.quaderno.sudoku

import android.content.Context
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class LevelStats(
    val started: Int, val completed: Int, val abandoned: Int,
    val totalSeconds: Long, val bestSeconds: Int, val bestScore: Int, val flawless: Int
)

internal data class ChallengeResult(
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
    var pastelTheme by mutableStateOf(prefs.getBoolean("pastel_theme", false))
        private set
    var playerName by mutableStateOf(prefs.getString("player_name", "Giocatore").orEmpty())
        private set
    var language by mutableStateOf(
        AppLanguage.values().firstOrNull { it.code == prefs.getString("language", "").orEmpty() } ?: AppLanguage.SYSTEM
    )
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

    fun updatePastelTheme(value: Boolean) {
        pastelTheme = value
        prefs.edit().putBoolean("pastel_theme", value).apply()
    }

    fun updatePlayerName(value: String) {
        playerName = value.trim().take(20).ifBlank { "Giocatore" }
        prefs.edit().putString("player_name", playerName).apply()
    }

    fun updateLanguage(value: AppLanguage) {
        language = value
        prefs.edit().putString("language", value.code).apply()
    }
}

internal class StatsStore(context: Context) {
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

    fun recordDailyCompletion(date: LocalDate, mistakes: Int) {
        val completed = prefs.getStringSet("daily_completed_dates", emptySet()).orEmpty().toMutableSet()
        completed.add(date.toString())
        val flawless = prefs.getStringSet("daily_flawless_dates", emptySet()).orEmpty().toMutableSet()
        if (mistakes == 0) flawless.add(date.toString())
        prefs.edit()
            .putStringSet("daily_completed_dates", completed)
            .putStringSet("daily_flawless_dates", flawless)
            .putInt("best_daily_flawless_streak", calculateBestDateStreak(flawless))
            .apply()
    }

    fun currentDailyFlawlessStreak(today: LocalDate = LocalDate.now()): Int {
        val completed = prefs.getStringSet("daily_completed_dates", emptySet()).orEmpty()
        val flawless = prefs.getStringSet("daily_flawless_dates", emptySet()).orEmpty()
        if (today.toString() in completed && today.toString() !in flawless) return 0
        var date = if (today.toString() in flawless) today else today.minusDays(1)
        var count = 0
        while (date.toString() in flawless) {
            count++
            date = date.minusDays(1)
        }
        return count
    }

    fun bestDailyFlawlessStreak(): Int = prefs.getInt("best_daily_flawless_streak", 0)

    private fun calculateBestDateStreak(values: Set<String>): Int {
        val dates = values.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0
        var current = 0
        var previous: LocalDate? = null
        dates.forEach { date ->
            current = if (previous != null && date == previous!!.plusDays(1)) current + 1 else 1
            best = maxOf(best, current)
            previous = date
        }
        return best
    }

    private fun updateStreak(won: Boolean) {
        val current = if (won) prefs.getInt("currentStreak", 0) + 1 else 0
        prefs.edit().putInt("currentStreak", current)
            .putInt("bestStreak", maxOf(prefs.getInt("bestStreak", 0), current)).apply()
    }

    fun currentStreak() = prefs.getInt("currentStreak", 0)
    fun bestStreak() = prefs.getInt("bestStreak", 0)
}
