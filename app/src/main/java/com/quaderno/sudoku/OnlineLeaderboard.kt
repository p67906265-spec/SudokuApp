package com.quaderno.sudoku

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.time.LocalDate

internal data class LeaderboardEntry(val name: String, val seconds: Int, val score: Int, val mistakes: Int)

internal data class DailyLeaderboard(
    val participants: Int = 0,
    val position: Int? = null,
    val top: List<LeaderboardEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

internal class OnlineLeaderboard(context: Context) {
    private val available = FirebaseApp.getApps(context).isNotEmpty()
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun withUser(onReady: (String) -> Unit, onError: () -> Unit) {
        if (!available) return onError()
        auth.currentUser?.uid?.let(onReady) ?: auth.signInAnonymously()
            .addOnSuccessListener { it.user?.uid?.let(onReady) ?: onError() }
            .addOnFailureListener { onError() }
    }

    fun submitAndLoad(date: LocalDate, name: String, seconds: Int, score: Int, mistakes: Int, onResult: (DailyLeaderboard) -> Unit) {
        onResult(DailyLeaderboard(loading = true))
        withUser({ uid ->
            val results = db.collection("daily_challenges").document(date.toString()).collection("results")
            val mine = results.document(uid)
            mine.get().addOnSuccessListener { previous ->
                val oldSeconds = previous.getLong("seconds")?.toInt()
                val bestSeconds = if (oldSeconds == null) seconds else minOf(oldSeconds, seconds)
                val data = mapOf(
                    "uid" to uid,
                    "name" to name.trim().take(20).ifBlank { "Giocatore" },
                    "seconds" to bestSeconds,
                    "score" to maxOf(previous.getLong("score")?.toInt() ?: 0, score),
                    "mistakes" to if (oldSeconds == null || seconds <= oldSeconds) mistakes else (previous.getLong("mistakes")?.toInt() ?: mistakes),
                    "completedAt" to System.currentTimeMillis()
                )
                mine.set(data, SetOptions.merge()).addOnSuccessListener { load(date, bestSeconds, onResult) }
                    .addOnFailureListener { onResult(DailyLeaderboard(error = "Classifica non disponibile")) }
            }.addOnFailureListener { onResult(DailyLeaderboard(error = "Classifica non disponibile")) }
        }, { onResult(DailyLeaderboard(error = "Connessione alla classifica non disponibile")) })
    }

    fun submitLockedAndLoad(date: LocalDate, name: String, seconds: Int, mistakes: Int, onResult: (DailyLeaderboard) -> Unit) {
        onResult(DailyLeaderboard(loading = true))
        val rankingValue = mistakes.toLong() * 1_000_000L + seconds
        withUser({ uid ->
            val results = db.collection("locked_challenges").document(date.toString()).collection("results")
            val mine = results.document(uid)
            mine.get().addOnSuccessListener { previous ->
                val oldRanking = previous.getLong("rankingValue")
                if (oldRanking != null && oldRanking <= rankingValue) {
                    loadLocked(date, oldRanking, onResult)
                } else {
                    val data = mapOf(
                        "uid" to uid,
                        "name" to name.trim().take(20).ifBlank { "Giocatore" },
                        "seconds" to seconds,
                        "score" to 0,
                        "mistakes" to mistakes,
                        "rankingValue" to rankingValue,
                        "completedAt" to System.currentTimeMillis()
                    )
                    mine.set(data, SetOptions.merge()).addOnSuccessListener { loadLocked(date, rankingValue, onResult) }
                        .addOnFailureListener { onResult(DailyLeaderboard(error = "Classifica non disponibile")) }
                }
            }.addOnFailureListener { onResult(DailyLeaderboard(error = "Classifica non disponibile")) }
        }, { onResult(DailyLeaderboard(error = "Connessione alla classifica non disponibile")) })
    }

    private fun load(date: LocalDate, mySeconds: Int, onResult: (DailyLeaderboard) -> Unit) {
        val results = db.collection("daily_challenges").document(date.toString()).collection("results")
        results.orderBy("seconds", Query.Direction.ASCENDING).limit(10).get().addOnSuccessListener { topSnapshot ->
            val top = topSnapshot.documents.map { doc ->
                LeaderboardEntry(doc.getString("name") ?: "Giocatore", doc.getLong("seconds")?.toInt() ?: 0,
                    doc.getLong("score")?.toInt() ?: 0, doc.getLong("mistakes")?.toInt() ?: 0)
            }
            results.count().get(AggregateSource.SERVER).addOnSuccessListener { total ->
                results.whereLessThan("seconds", mySeconds).count().get(AggregateSource.SERVER)
                    .addOnSuccessListener { ahead ->
                        onResult(DailyLeaderboard(total.count.toInt(), ahead.count.toInt() + 1, top))
                    }
                    .addOnFailureListener { onResult(DailyLeaderboard(total.count.toInt(), null, top, error = "Posizione non disponibile")) }
            }.addOnFailureListener { onResult(DailyLeaderboard(top = top, error = "Conteggio non disponibile")) }
        }.addOnFailureListener { onResult(DailyLeaderboard(error = "Classifica non disponibile")) }
    }

    private fun loadLocked(date: LocalDate, myRanking: Long, onResult: (DailyLeaderboard) -> Unit) {
        val results = db.collection("locked_challenges").document(date.toString()).collection("results")
        results.orderBy("rankingValue", Query.Direction.ASCENDING).limit(10).get().addOnSuccessListener { snapshot ->
            val top = snapshot.documents.map { doc ->
                LeaderboardEntry(doc.getString("name") ?: "Giocatore", doc.getLong("seconds")?.toInt() ?: 0,
                    0, doc.getLong("mistakes")?.toInt() ?: 0)
            }
            results.count().get(AggregateSource.SERVER).addOnSuccessListener { total ->
                results.whereLessThan("rankingValue", myRanking).count().get(AggregateSource.SERVER)
                    .addOnSuccessListener { ahead ->
                        onResult(DailyLeaderboard(total.count.toInt(), ahead.count.toInt() + 1, top))
                    }
                    .addOnFailureListener { onResult(DailyLeaderboard(total.count.toInt(), null, top, error = "Posizione non disponibile")) }
            }.addOnFailureListener { onResult(DailyLeaderboard(top = top, error = "Conteggio non disponibile")) }
        }.addOnFailureListener { onResult(DailyLeaderboard(error = "Classifica non disponibile")) }
    }
}
