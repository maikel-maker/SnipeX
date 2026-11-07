package be.maikel.xplorer.domain

import kotlin.math.floor
import kotlin.math.min

/**
 * Rééquilibrage par le haut, sans jamais sortir en euros : quand une crypto dépasse nettement sa cible,
 * on en déplace une partie vers celles qui sont en retard. Le but reste d'accumuler.
 */
object Rebalance {

    data class Move(
        val from: String,
        val to: String,
        val eur: Double,
        /** Paire Coinbase à utiliser en un seul ordre, ou null s'il faut passer par l'euro (2 ordres). */
        val pair: String?,
        val instruction: String,
    )

    data class Summary(val moves: List<Move>, val reason: String)

    /**
     * @param values valeur en euros de chaque crypto détenue
     * @param targets répartition cible (0..1)
     * @param pairs paires Coinbase disponibles (ex. "ETH-BTC", "BTC-EUR")
     * @param locked cryptos qu'on ne vend jamais (ex. GEOD sous son seuil x20)
     * @param trigger déclenchement quand le poids dépasse la cible × trigger (1,25 = 25 % au-dessus)
     */
    fun compute(
        values: Map<String, Double>,
        targets: Map<String, Double>,
        pairs: Set<String>,
        locked: Set<String> = emptySet(),
        trigger: Double = 1.25,
        minMove: Double = 25.0,
    ): Summary? {
        val crypto = values.filterKeys { it in targets }.values.sum()
        if (crypto <= 0) return null
        val over = targets.filter { (s, t) ->
            t > 0 && s !in locked && (values[s] ?: 0.0) / crypto > t * trigger
        }.keys.associateWith { s -> (values[s] ?: 0.0) - targets.getValue(s) * crypto }
            .filterValues { it >= minMove }
        if (over.isEmpty()) return null
        val deficit = targets.filter { (s, t) -> t > 0 && (values[s] ?: 0.0) / crypto < t }
            .keys.associateWith { s -> targets.getValue(s) * crypto - (values[s] ?: 0.0) }
            .toMutableMap()
        val moves = mutableListOf<Move>()
        for ((from, excess0) in over.entries.sortedByDescending { it.value }) {
            var excess = excess0
            for (to in deficit.entries.sortedByDescending { it.value }.map { it.key }) {
                val need = deficit[to] ?: 0.0
                val eur = floor(min(excess, need))
                if (eur < minMove) continue
                moves += Move(from, to, eur, pairFor(from, to, pairs), instruction(from, to, eur, pairs))
                excess -= eur
                deficit[to] = need - eur
                if (excess < minMove) break
            }
        }
        if (moves.isEmpty()) return null
        val names = over.keys.joinToString(" et ")
        return Summary(moves, "$names dépasse nettement sa cible : on en déplace une partie vers les cryptos en retard, sans repasser par l'euro.")
    }

    fun pairFor(from: String, to: String, pairs: Set<String>): String? = when {
        "$to-$from" in pairs -> "$to-$from"
        "$from-$to" in pairs -> "$from-$to"
        else -> null
    }

    private fun quote(sym: String, pairs: Set<String>): String =
        listOf("EUR", "USDC", "USD").firstOrNull { "$sym-$it" in pairs } ?: "EUR"

    fun instruction(from: String, to: String, eur: Double, pairs: Set<String>): String {
        val e = Money.eur(eur, 0)
        return when {
            "$to-$from" in pairs -> "Sur $to-$from : achète $to pour $e, payé en $from (1 seul ordre)."
            "$from-$to" in pairs -> "Sur $from-$to : vends $e de $from, tu reçois des $to (1 seul ordre)."
            else -> {
                val q1 = quote(from, pairs); val q2 = quote(to, pairs)
                "Vends $e de $from sur $from-$q1, puis achète $to avec ce montant sur $to-$q2 (2 ordres)."
            }
        }
    }
}
