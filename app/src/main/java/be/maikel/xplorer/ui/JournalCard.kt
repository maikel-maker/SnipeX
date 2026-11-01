package be.maikel.xplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.maikel.xplorer.core.Analysis
import be.maikel.xplorer.domain.DcaJournal
import be.maikel.xplorer.domain.Money
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val dayFmt = SimpleDateFormat("d MMM", Locale.FRANCE).apply { timeZone = TimeZone.getTimeZone("UTC") }
private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.FRANCE)

/** Bandeau « ne panique pas » quand le Bitcoin chute fort sur 7 jours. */
@Composable
fun calmBanner(drop: Double) {
    val shape = RoundedCornerShape(20.dp)
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Feu.Orange.copy(alpha = 0.30f), Vivid.Violet.copy(alpha = 0.25f))))
            .border(1.dp, Feu.Orange, shape).padding(16.dp),
    ) {
        Text("🧘  Le marché baisse fort", style = MaterialTheme.typography.titleMedium, color = Feu.Orange, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Bitcoin ${Money.pct(drop, true)} en 7 jours. C'est là que ton DCA achète moins cher. Continue ton plan, ne vends pas sous le coup de la peur.",
            style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/** Mois (année × 12 + mois) d'une semaine, d'après son lundi. */
private fun monthKey(w: DcaJournal.Week): Int =
    Calendar.getInstance().apply { timeInMillis = w.start + 43_200_000L }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

private fun monthName(w: DcaJournal.Week): String =
    monthFmt.format(Date(w.start + 43_200_000L)).replaceFirstChar { it.uppercase() }

/** Mon suivi DCA : mois par mois, ce que tu as acheté face à ton budget, puis le détail des semaines. */
@Composable
fun journalCard(a: Analysis) {
    val j = a.journal ?: return
    if (j.weeks.isEmpty()) return
    var older by rememberSaveable { mutableStateOf(false) }
    val months = j.weeks.groupBy { monthKey(it) }.entries.sortedByDescending { it.key }
    val currentMonday = DcaJournal.weekStart(System.currentTimeMillis())

    SectionTitle("📒 Mon suivi DCA")
    Glass(padding = 18) {
        months.forEachIndexed { idx, (_, weeks) ->
            if (idx > 0 && !older) return@forEachIndexed
            val bought = weeks.sumOf { it.bought }
            val budget = weeks.sumOf { it.budget }
            val progress = if (budget > 0) bought / budget else 0.0
            if (idx > 0) { Spacer(Modifier.height(14.dp)); Divider(); Spacer(Modifier.height(14.dp)) }

            // En-tête du mois : acheté / budget, avec une barre.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(monthName(weeks.first()), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${Money.eur(bought, 0)} / ${Money.eur(budget, 0)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            GradientBar(progress, listOf(Vivid.Violet, Vivid.Pink, Vivid.Sun))
            Spacer(Modifier.height(4.dp))
            Muted(
                when {
                    progress >= 0.98 -> "Mois bouclé 🎉 Budget investi en entier."
                    weeks.any { it.start == currentMonday } -> "Encore ${Money.eur((budget - bought).coerceAtLeast(0.0), 0)} à investir ce mois-ci."
                    else -> "${Money.eur((budget - bought).coerceAtLeast(0.0), 0)} non investis ce mois-là."
                },
            )
            Spacer(Modifier.height(10.dp))
            // Une ligne par semaine, du lundi le plus récent au plus ancien.
            weeks.sortedByDescending { it.start }.forEach { w -> weekRow(w, w.start == currentMonday) }
        }
        if (months.size > 1) TextButton(onClick = { older = !older }) { Text(if (older) "Masquer les mois précédents ▲" else "Voir les mois précédents ▼", color = Xp.Cyan) }

        // Frais réels lus chez Coinbase.
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Vivid.Sky.copy(alpha = 0.16f), Vivid.Violet.copy(alpha = 0.16f))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧾", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Frais réels : ${Money.eur(j.totalFees, 2)} au total", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Muted("Sur ${Money.eur(j.totalBought, 0)} achetés en ${j.buyCount} ordre(s) : ${Money.pct(j.feePct)}, soit ≈ ${Money.eur(j.feePerOrder, 2)} par ordre. Chiffres lus chez Coinbase.")
            }
        }
    }
}

@Composable
private fun weekRow(w: DcaJournal.Week, current: Boolean) {
    val c = when { w.done -> Feu.Vert; current -> Vivid.Sky; w.partial -> Feu.Orange; else -> Feu.Rouge }
    val status = when {
        w.done -> "✅"
        current -> "⏳"
        w.partial -> "🟠"
        else -> "❌"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (current) "Cette semaine" else "Sem. du ${dayFmt.format(Date(w.start))}", style = MaterialTheme.typography.bodyMedium, color = Xp.Ice, modifier = Modifier.width(118.dp))
            // Barre de la semaine : remplie selon le budget.
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.08f))) {
                val p = if (w.budget > 0) (w.bought / w.budget).toFloat().coerceIn(0f, 1f) else 0f
                if (p > 0.01f) Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(c))
            }
            Spacer(Modifier.width(10.dp))
            Text("${Money.eur(w.bought, 0)} $status", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        if (w.bySymbol.isNotEmpty())
            Muted(w.bySymbol.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${Money.eur(it.value, 0)}" }, Modifier.padding(start = 118.dp))
    }
}
