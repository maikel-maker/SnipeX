package be.maikel.xplorer.domain

import java.util.Calendar
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val DAY = 86_400_000L

/**
 * Budget de la semaine (choix de Maïkel, 05/11/2026) : 250 € les 4 premiers lundis du mois,
 * jamais plus que 1 000 € par mois. S'il y a un 5e lundi, c'est une semaine de pause.
 */
object Budget {
    /** Rang du lundi [mondayMs] dans son mois : 1 (du 1er au 7) à 5 (du 29 au 31). */
    fun mondayRank(mondayMs: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = mondayMs + 43_200_000L }
        return (c.get(Calendar.DAY_OF_MONTH) - 1) / 7 + 1
    }

    fun week(weekly: Double, monthly: Double, mondayMs: Long): Double =
        max(0.0, min(weekly, monthly - (mondayRank(mondayMs) - 1) * weekly))
}

/**
 * Le Chasseur de creux : 40 % du budget de la semaine s'achètent tout de suite.
 * Le reste attend que la crypto baisse d'au moins 3 % par rapport à l'ouverture du lundi.
 * Pas de creux avant vendredi ? On achète le reste vendredi, on ne garde jamais l'argent en attente plus longtemps.
 */
object DipHunter {
    const val FIRST_SHARE = 0.4
    const val DIP = 0.03

    enum class State { NOW, WAIT, DIP, DEADLINE }

    data class Line(val symbol: String, val nowEur: Double, val laterEur: Double, val change: Double?, val state: State)

    data class Result(val lines: List<Line>, val deadline: Boolean) {
        /** Cryptos dont le reste est à acheter maintenant (creux atteint ou vendredi). */
        val ready get() = lines.filter { (it.state == State.DIP || it.state == State.DEADLINE) && it.laterEur >= 1 }
        fun line(sym: String) = lines.firstOrNull { it.symbol == sym }
    }

    /**
     * [lines] : ce qu'il reste à acheter cette semaine par crypto. [weekBought] : déjà acheté cette semaine.
     * [mondayOpen] : prix à l'ouverture du lundi. [now] décide si on est vendredi ou plus tard.
     */
    fun compute(
        lines: Map<String, Double>, weekBudget: Double, weekBought: Double,
        mondayOpen: Map<String, Double>, prices: Map<String, Double>, now: Long,
    ): Result {
        val remaining = lines.values.filter { it > 0 }.sum()
        val firstLeft = max(0.0, weekBudget * FIRST_SHARE - weekBought)
        val share = if (remaining > 0) min(1.0, firstLeft / remaining) else 0.0
        val deadline = isDeadline(now)
        val out = lines.filter { it.value > 0 }.map { (sym, eur) ->
            val nowEur = min(eur, floor(eur * share + 0.5))
            val later = eur - nowEur
            val open = mondayOpen[sym]
            val p = prices[sym]
            val ch = if (open != null && open > 0 && p != null && p > 0) p / open - 1 else null
            val state = when {
                later < 1 -> State.NOW
                deadline -> State.DEADLINE
                ch != null && ch <= -DIP -> State.DIP
                else -> State.WAIT
            }
            Line(sym, nowEur, later, ch, state)
        }
        return Result(out, deadline)
    }

    /** Vendredi (heure locale) ou le week-end : le reste s'achète, creux ou pas. */
    fun isDeadline(now: Long): Boolean {
        val d = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.FRIDAY || d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    /** Prix d'ouverture de la journée du lundi [monday] (bougies journalières à minuit UTC). */
    fun mondayOpen(daily: Map<String, List<Candle>>, monday: Long): Map<String, Double> =
        daily.mapNotNull { (sym, c) -> c.firstOrNull { it.t >= monday && it.t < monday + DAY }?.open?.takeIf { it > 0 }?.let { sym to it } }.toMap()
}

/**
 * Le Miroir : tes vrais achats comparés à trois versions de toi qui n'existent pas,
 * avec exactement le même argent aux mêmes dates.
 */
object Mirror {
    data class Result(
        val invested: Double,
        /** Ta méthode : la valeur aujourd'hui de ce que tu as vraiment acheté. */
        val mine: Double,
        /** Toi version tout en Bitcoin. */
        val allBtc: Double?,
        /** Toi version répartition fixe, sans rattrapage. */
        val fixedSplit: Double?,
        /** Toi version tout investi d'un coup, le jour du premier achat. */
        val lumpSum: Double?,
        val buys: Int,
        val since: Long,
    ) {
        /** Classement des 4 versions, de la meilleure à la moins bonne (null = données manquantes). */
        val ranking: List<Pair<String, Double>>
            get() = listOfNotNull(
                "Ta méthode" to mine,
                allBtc?.let { "Tout en Bitcoin" to it },
                fixedSplit?.let { "Sans rattrapage" to it },
                lumpSum?.let { "Tout d'un coup" to it },
            ).sortedByDescending { it.second }
    }

    fun compute(trades: List<Trade>, from: Long, daily: Map<String, List<Candle>>, prices: Map<String, Double>, targets: Map<String, Double>): Result? {
        val buys = trades.filter { it.buy && it.t >= from && it.totalEur > 0 && it.qty > 0 }
        if (buys.isEmpty()) return null
        val invested = buys.sumOf { it.totalEur }
        val mine = buys.sumOf { b -> prices[b.symbol]?.takeIf { it > 0 }?.let { it * b.qty } ?: b.totalEur }

        fun close(sym: String, t: Long): Double? = daily[sym]?.lastOrNull { it.t <= t }?.close?.takeIf { it > 0 }
        fun grow(sym: String, eur: Double, t: Long): Double? {
            val c = close(sym, t) ?: return null
            val p = prices[sym]?.takeIf { it > 0 } ?: return null
            return eur / c * p
        }
        val tsum = targets.values.sum()
        fun split(eur: Double, t: Long): Double? {
            if (tsum <= 0) return null
            var s = 0.0
            for ((sym, w) in targets) s += grow(sym, eur * w / tsum, t) ?: return null
            return s
        }
        fun total(f: (Trade) -> Double?): Double? {
            var s = 0.0
            for (b in buys) s += f(b) ?: return null
            return s
        }
        val first = buys.minOf { it.t }
        return Result(
            invested, mine,
            total { grow("BTC", it.totalEur, it.t) },
            total { split(it.totalEur, it.t) },
            split(invested, first),
            buys.size, first,
        )
    }
}

/**
 * Cap 2030 : où t'emmènent 1 000 € par mois. Trois hypothèses de marché (pas des prévisions),
 * et la date estimée de tes prochains grands paliers dans l'hypothèse du milieu.
 */
object Cap {
    data class Scenario(val label: String, val yearly: Double, val finalValue: Double)
    /** [months] : dans combien de mois le palier est atteint (0 = déjà atteint, null = pas avant 20 ans). */
    data class Goal(val label: String, val months: Int?)
    data class Result(val endYear: Int, val months: Int, val invested: Double, val scenarios: List<Scenario>, val goals: List<Goal>)

    const val CENTRAL = 0.15

    fun project(
        now: Long, value: Double, monthly: Double, qty: Map<String, Double>, prices: Map<String, Double>,
        targets: Map<String, Double>, endYear: Int = 2030,
    ): Result {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val months = max(1, (endYear - c.get(Calendar.YEAR)) * 12 + (11 - c.get(Calendar.MONTH)) + 1)
        val scenarios = listOf("Le marché stagne" to 0.0, "+15 % par an" to CENTRAL, "+35 % par an" to 0.35).map { (label, y) ->
            val r = (1 + y).pow(1.0 / 12) - 1
            var v = value
            repeat(months) { v = v * (1 + r) + monthly }
            Scenario(label, y, v)
        }
        val r = (1 + CENTRAL).pow(1.0 / 12) - 1
        val tsum = targets.values.sum().takeIf { it > 0 } ?: 1.0
        val coinGoals = listOf(Triple("Posséder 0,1 BTC", "BTC", 0.1), Triple("Posséder 1 ETH", "ETH", 1.0)).mapNotNull { (label, sym, goal) ->
            var p = prices[sym]?.takeIf { it > 0 } ?: return@mapNotNull null
            var q = qty[sym] ?: 0.0
            var m: Int? = if (q >= goal) 0 else null
            var i = 0
            while (m == null && i < 240) {
                i++
                p *= (1 + r)
                q += monthly * (targets[sym] ?: 0.0) / tsum / p
                if (q >= goal) m = i
            }
            Goal(label, m)
        }
        val valueGoals = listOf(10_000.0 to "10 000 € de cryptos", 50_000.0 to "50 000 € de cryptos").map { (goal, label) ->
            var v = value
            var m: Int? = if (v >= goal) 0 else null
            var i = 0
            while (m == null && i < 240) {
                i++
                v = v * (1 + r) + monthly
                if (v >= goal) m = i
            }
            Goal(label, m)
        }
        return Result(endYear, months, monthly * months, scenarios, coinGoals + valueGoals)
    }
}

/** La Sentinelle GEOD : ta règle « je ne vends qu'à ×20 », surveillée en continu. */
object Sentinel {
    data class Result(
        val price: Double, val base: Double, val multiple: Double, val target: Double,
        val qty: Double, val valueNow: Double, val valueAtTarget: Double,
        /** Niveaux d'alerte (×5, ×10, ×20) et le prochain à franchir. */
        val levels: List<Double>, val next: Double?,
    ) {
        val reachedTarget get() = multiple >= target
    }

    fun levels(target: Double): List<Double> = (listOf(5.0, 10.0).filter { it < target } + target)

    fun compute(price: Double?, base: Double, target: Double, qty: Double): Result? {
        if (price == null || !(price > 0) || !(base > 0) || !(target > 1)) return null
        val m = price / base
        val lv = levels(target)
        return Result(price, base, m, target, qty, qty * price, qty * base * target, lv, lv.firstOrNull { m < it })
    }

    /** Plus haut niveau franchi (0 si aucun). */
    fun levelReached(multiple: Double, target: Double): Int = levels(target).lastOrNull { multiple >= it }?.toInt() ?: 0
}

/**
 * Le Gardien : quand le marché chute fort, il te montre tes propres achats faits pendant les baisses passées,
 * et ce qu'ils valent aujourd'hui. Des faits, pas des promesses.
 */
object Guardian {
    const val TRIGGER = -0.10

    data class Buy(val t: Long, val symbol: String, val eur: Double, val unit: Double, val valueNow: Double) {
        val gain get() = valueNow - eur
        val pct get() = if (eur > 0) valueNow / eur - 1 else 0.0
    }

    data class Result(val drop: Double, val dipBuys: List<Buy>, val bestBuys: List<Buy>)

    fun compute(btcWeek: Double?, trades: List<Trade>, daily: Map<String, List<Candle>>, prices: Map<String, Double>): Result? {
        val d = btcWeek ?: return null
        if (d > TRIGGER) return null
        val buys = trades.filter { it.buy && it.totalEur > 1 && it.qty > 0 }.mapNotNull { b ->
            val p = prices[b.symbol]?.takeIf { it > 0 } ?: return@mapNotNull null
            Buy(b.t, b.symbol, b.totalEur, b.unitEur, b.qty * p)
        }
        // Achat « dans un creux » : payé au moins 5 % moins cher qu'une semaine avant.
        val dip = buys.filter { b ->
            val before = daily[b.symbol]?.lastOrNull { it.t <= b.t - 7 * DAY }?.close
            before != null && before > 0 && b.unit > 0 && b.unit <= before * 0.95
        }.sortedByDescending { it.pct }.take(3)
        return Result(d, dip, buys.sortedByDescending { it.pct }.take(3))
    }
}
