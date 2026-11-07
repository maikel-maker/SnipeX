package be.maikel.xplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.maikel.xplorer.domain.Cap
import be.maikel.xplorer.domain.DipHunter
import be.maikel.xplorer.domain.Guardian
import be.maikel.xplorer.domain.Mirror
import be.maikel.xplorer.domain.Rebalance
import be.maikel.xplorer.domain.Money
import be.maikel.xplorer.domain.Sentinel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ln

private val monthYear = SimpleDateFormat("MMMM yyyy", Locale.FRANCE)
private val dayMonth = SimpleDateFormat("d MMM", Locale.FRANCE)

private fun inMonths(months: Int): String {
    val c = Calendar.getInstance().apply { add(Calendar.MONTH, months) }
    return monthYear.format(c.time).replaceFirstChar { it.uppercase() }
}

// ─────────────────────────────── Chasseur de creux ───────────────────────────────

/** Explication en une ligne, en haut de la liste de la semaine. */
@Composable
fun hunterIntro(h: DipHunter.Result) {
    if (h.lines.isEmpty()) return
    val ready = h.ready.size
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp).clip(RoundedCornerShape(12.dp))
            .background(Xp.Cyan.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Chasseur de creux", style = MaterialTheme.typography.labelLarge, color = Xp.Cyan)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                h.deadline -> "on est vendredi : tout s'achète aujourd'hui"
                ready > 0 -> "$ready creux détecté${if (ready > 1) "s" else ""}, c'est le moment"
                else -> "40 % aujourd'hui, le reste attend une baisse de 3 %"
            },
            style = MaterialTheme.typography.labelMedium, color = Xp.Ice,
        )
    }
}

/** Sous chaque crypto : la tranche à acheter maintenant et celle qui attend un creux. */
@Composable
fun dipLine(l: DipHunter.Line) {
    Spacer(Modifier.height(6.dp))
    val total = l.nowEur + l.laterEur
    val s = MaterialTheme.typography.labelSmall
    when {
        // Rien en attente, ou vendredi / creux atteint : tout s'achète aujourd'hui, une seule phrase.
        l.laterEur < 1 || l.state == DipHunter.State.DEADLINE ->
            Text("Achète ${Money.eur(total, 0)} aujourd'hui", style = s, color = Xp.Gold)
        l.state == DipHunter.State.DIP ->
            Text("Creux atteint : achète ${Money.eur(total, 0)} aujourd'hui", style = s, color = Vivid.Bulls)
        else -> Column {
            if (l.nowEur >= 1) Text("Achète ${Money.eur(l.nowEur, 0)} aujourd'hui", style = s, color = Xp.Ice)
            val ch = l.change?.let { " · ${Money.pct(it, true)} depuis lundi" } ?: ""
            Text(
                "${Money.eur(l.laterEur, 0)} en attente d'une baisse de 3 % (au plus tard vendredi)$ch",
                style = s, color = Xp.Slate,
            )
        }
    }
}

// ─────────────────────────────── Gardien ───────────────────────────────

@Composable
fun guardianCard(g: Guardian.Result) {
    val shape = RoundedCornerShape(22.dp)
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF0E1B2E))
            .border(1.dp, Xp.Cyan.copy(alpha = 0.45f), shape).padding(18.dp),
    ) {
        Text("Le Gardien", style = MaterialTheme.typography.labelLarge, color = Xp.Cyan)
        Spacer(Modifier.height(2.dp))
        Text("Le marché baisse : Bitcoin ${Money.pct(g.drop, true)} en 7 jours", style = MaterialTheme.typography.titleMedium, color = Xp.Ice)
        Spacer(Modifier.height(6.dp))
        val list = g.dipBuys.ifEmpty { g.bestBuys }
        Text(
            if (g.dipBuys.isNotEmpty()) "La dernière fois que ça a baissé, tu as acheté. Voilà ce que ces achats valent aujourd'hui :"
            else "Tes meilleurs achats jusqu'ici, pour garder la tête froide :",
            style = MaterialTheme.typography.bodyMedium, color = Xp.Slate,
        )
        Spacer(Modifier.height(8.dp))
        list.forEach { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${coinName(b.symbol)} · ${dayMonth.format(Date(b.t))}", style = MaterialTheme.typography.labelLarge, color = Xp.Ice)
                    Muted("${Money.eur(b.eur, 0)} investis → ${Money.eur(b.valueNow, 0)} aujourd'hui")
                }
                Text(Money.pct(b.pct, true), style = MaterialTheme.typography.titleMedium, color = if (b.pct >= 0) Vivid.Bulls else Vivid.Bears)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Ton plan ne change pas : cette semaine, ton DCA achète moins cher.", style = MaterialTheme.typography.bodyMedium, color = Xp.Ice)
    }
}

// ─────────────────────────────── Miroir ───────────────────────────────

@Composable
fun mirrorCard(m: Mirror.Result) {
    SectionTitle("Le Miroir")
    Glass(padding = 18) {
        Muted("Les ${Money.eur(m.invested, 0)} que tu as investis depuis le ${dayMonth.format(Date(m.since))}, rejoués de 4 façons. Même argent, mêmes dates.", small = false)
        Spacer(Modifier.height(12.dp))
        val rank = m.ranking
        val best = rank.firstOrNull()?.second ?: m.mine
        val worst = rank.lastOrNull()?.second ?: m.mine
        rank.forEachIndexed { i, (label, v) ->
            val mine = label == "Ta méthode"
            val gain = v - m.invested
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (mine) Xp.Cyan.copy(alpha = 0.10f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = if (mine) Xp.Cyan else Xp.Slate, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = if (mine) Xp.Cyan else Xp.Ice, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal)
                    val span = (best - worst).takeIf { it > 0.01 }
                    Spacer(Modifier.height(4.dp))
                    GradientBar(span?.let { 0.15 + 0.85 * (v - worst) / it } ?: 1.0, if (mine) listOf(Xp.CyanDeep, Xp.Cyan) else listOf(Xp.Slate.copy(alpha = 0.5f), Xp.Slate))
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.eur(v, 0), style = MaterialTheme.typography.titleMedium, color = Xp.Ice)
                    Text(Money.signedEur(gain, 0), style = MaterialTheme.typography.labelSmall, color = if (gain >= 0) Vivid.Bulls else Vivid.Bears)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val place = rank.indexOfFirst { it.first == "Ta méthode" } + 1
        val ahead = rank.firstOrNull { it.first != "Ta méthode" }
        Text(
            when {
                place == 1 && ahead != null -> "Ta méthode est en tête, ${Money.eur(m.mine - ahead.second, 0)} devant « ${ahead.first} »."
                place > 1 -> "Ta méthode est ${place}e. Normal sur quelques semaines : on juge sur plusieurs mois."
                else -> "Pas encore assez de données pour comparer."
            },
            style = MaterialTheme.typography.bodyMedium, color = Xp.Ice,
        )
        if (rank.size < 4) Muted("Certaines versions manquent : pas assez d'historique de prix pour les rejouer.")
    }
}

// ─────────────────────────────── Sentinelle GEOD ───────────────────────────────

@Composable
fun sentinelCard(s: Sentinel.Result) {
    SectionTitle("Sentinelle GEOD")
    Glass(padding = 18) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Muted("Prix actuel ÷ ton prix d'achat")
                Text("×${"%.1f".format(Locale.FRANCE, s.multiple)}", style = MaterialTheme.typography.headlineMedium, color = if (s.reachedTarget) Xp.Gold else Xp.Ice)
            }
            Column(horizontalAlignment = Alignment.End) {
                Muted("Ta règle")
                Text("vendre à ×${s.target.toInt()}", style = MaterialTheme.typography.titleMedium, color = Xp.Gold)
            }
        }
        Spacer(Modifier.height(14.dp))
        // Échelle logarithmique : ×1 à gauche, ×cible à droite.
        val top = ln(s.target)
        val pos = (ln(s.multiple.coerceAtLeast(1.0)) / top).coerceIn(0.0, 1.0)
        GradientBar(pos.coerceAtLeast(0.02), listOf(Xp.CyanDeep, Xp.Cyan, Xp.Gold))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("×1", style = MaterialTheme.typography.labelSmall, color = Xp.Slate, modifier = Modifier.weight(1f))
            s.levels.forEach { lv ->
                val hit = s.multiple >= lv
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (hit) Xp.Gold else Xp.Edge2))
                    Spacer(Modifier.width(4.dp))
                    Text("×${lv.toInt()}", style = MaterialTheme.typography.labelSmall, color = if (hit) Xp.Gold else Xp.Slate)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tes ${Money.qty(s.qty)} GEOD valent ${Money.eur(s.valueNow, 0)} aujourd'hui, et vaudraient ${Money.eur(s.valueAtTarget, 0)} à ×${s.target.toInt()}.",
            style = MaterialTheme.typography.bodyMedium, color = Xp.Ice,
        )
        Spacer(Modifier.height(4.dp))
        Muted(
            if (s.reachedTarget) "×${s.target.toInt()} atteint ! C'est toi qui décides combien vendre. Idée : vendre en plusieurs fois (un quart à ×20, un quart à ×25…) plutôt que tout d'un coup."
            else s.next?.let { "Prochaine alerte à ×${it.toInt()}, soit ${Money.price(s.base * it)} par GEOD. Tu seras prévenu." } ?: "",
        )
    }
}

// ─────────────────────────────── Cap 2030 ───────────────────────────────

@Composable
fun capCard(c: Cap.Result) {
    SectionTitle("Cap ${c.endYear}")
    Glass(padding = 18) {
        Muted("Avec ${Money.eur(c.invested / c.months, 0)} par mois jusqu'à fin ${c.endYear}, tu auras investi ${Money.eur(c.invested, 0)} de plus. Ce que ça pourrait donner :", small = false)
        Spacer(Modifier.height(12.dp))
        val max = c.scenarios.maxOfOrNull { it.finalValue } ?: 1.0
        c.scenarios.forEach { sc ->
            val central = sc.yearly == Cap.CENTRAL
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sc.label, style = MaterialTheme.typography.labelLarge, color = if (central) Xp.Cyan else Xp.Ice)
                    Spacer(Modifier.height(4.dp))
                    GradientBar(sc.finalValue / max, if (central) listOf(Xp.CyanDeep, Xp.Cyan) else listOf(Xp.Slate.copy(alpha = 0.4f), Xp.Slate))
                }
                Spacer(Modifier.width(12.dp))
                Text(Money.eur(sc.finalValue, 0), style = MaterialTheme.typography.titleMedium, color = if (central) Xp.Cyan else Xp.Ice)
            }
        }
        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(10.dp))
        Text("Tes prochains caps (hypothèse +15 % par an)", style = MaterialTheme.typography.labelLarge, color = Xp.Gold)
        Spacer(Modifier.height(6.dp))
        c.goals.forEach { g ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g.label, style = MaterialTheme.typography.bodyMedium, color = Xp.Ice, modifier = Modifier.weight(1f))
                Text(
                    when (val m = g.months) { null -> "après 2045"; 0 -> "atteint ✓"; else -> inMonths(m) },
                    style = MaterialTheme.typography.labelLarge, color = if (g.months == 0) Vivid.Bulls else Xp.Ice,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Muted("Des hypothèses, pas des promesses : la crypto peut aussi baisser fortement.")
    }
}


// ─────────────────────────────── Rééquilibrage ───────────────────────────────

@Composable
fun rebalanceCard(r: Rebalance.Summary) {
    SectionTitle("Rééquilibrage conseillé")
    Glass(padding = 18) {
        Text(r.reason, style = MaterialTheme.typography.bodyMedium, color = Xp.Ice)
        Spacer(Modifier.height(10.dp))
        r.moves.forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                    .background(Xp.Cyan.copy(alpha = 0.08f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${m.from} → ${m.to}", style = MaterialTheme.typography.titleMedium, color = Xp.Ice, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(m.instruction, style = MaterialTheme.typography.bodyMedium, color = Xp.Ice)
                }
                Spacer(Modifier.width(10.dp))
                Text(Money.eur(m.eur, 0), style = MaterialTheme.typography.titleLarge, color = Xp.Gold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Muted("Tu restes 100 % investi : rien ne repasse en euros. Ordre limite conseillé (frais maker plus bas). GEOD n'est jamais vendu sous son seuil x20.")
    }
}
