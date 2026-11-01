package be.maikel.xplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import be.maikel.xplorer.core.Analysis
import be.maikel.xplorer.domain.Money
import java.util.Calendar
import androidx.compose.runtime.Composable

private fun hello(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bonjour Maïkel"
    in 12..17 -> "Bon après-midi Maïkel"
    else -> "Bonsoir Maïkel"
}

/**
 * Accueil SnipeX : ton argent, la consigne de la semaine, l'arène des agents.
 * Aucun mot technique ici : tout le détail reste dans l'onglet « Détails ».
 */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.simpleHome(
    a: Analysis?,
    hasKey: Boolean,
    onAsset: (String) -> Unit,
    onDetails: () -> Unit,
    onSettings: () -> Unit,
    onDone: (List<Pair<String, Double>>) -> Unit,
) {
    item {
        Text(hello(), style = MaterialTheme.typography.titleLarge, color = Xp.Ice, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp))
    }
    if (a == null) { item { Muted("Je prépare tes chiffres…", small = false) }; return }
    val l = a.ledger

    // 1. Ton argent, en une phrase.
    item {
        var detail by rememberSaveable { mutableStateOf(false) }
        Glass(padding = 22, onClick = if (hasKey) ({ detail = !detail }) else null) {
            if (hasKey) {
                // Juste les faits : ce que tu as mis en DCA, et ce que tu possèdes aujourd'hui.
                Muted("Mon DCA crypto", small = false)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Muted("J'ai mis", small = false)
                        Text(Money.eur(a.capital, 0), style = MaterialTheme.typography.headlineMedium, color = Xp.Ice)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Muted("Je possède", small = false)
                        Text(Money.eur(l.totalEur, 0), style = MaterialTheme.typography.headlineMedium, color = Xp.Ice)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (detail) "Masquer le détail ▲" else "D'où vient « J'ai mis » ? ▼", style = MaterialTheme.typography.labelLarge, color = Xp.Cyan)
                if (detail) {
                    Spacer(Modifier.height(8.dp))
                    val fmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.FRANCE)
                    a.capitalLines.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.label, style = MaterialTheme.typography.bodyMedium, color = Xp.Ice)
                                Muted(fmt.format(java.util.Date(c.t)))
                            }
                            Text(Money.signedEur(c.eur, 2), style = MaterialTheme.typography.titleSmall, color = if (c.eur >= 0) Vivid.Sky else Feu.Orange)
                        }
                    }
                    if (a.capitalLines.isEmpty()) Muted("Aucun mouvement lu depuis le départ.")
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Reste investi", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                        Text(Money.eur(a.capital, 2), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    if (a.capitalManual) Muted("Montant corrigé à la main dans les réglages.")
                    Spacer(Modifier.height(4.dp))
                    Muted("« J'ai mis » = tes virements vers Coinbase moins ce que tu as reviré vers ta banque, lus avec ta clé (lecture seule). C'est l'argent qui reste vraiment dans ta crypto.")
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Banner("Connecte ton compte Coinbase (lecture seule) pour voir ton vrai argent. Touche ici.", error = false) { onSettings() }
            }
        }
    }

    // Ne panique pas : grosse baisse du Bitcoin.
    a.btcWeek?.takeIf { it <= -0.15 }?.let { d -> item { calmBanner(d) } }

    // 2. La consigne de la semaine.
    // Ordre : de ta plus grosse crypto à la plus petite.
    val held = l.positions.associate { it.symbol to it.value }
    val buys = a.plan.lines.filter { it.eur > 0 }.sortedByDescending { held[it.symbol] ?: 0.0 }
    item {
        var buyMode by rememberSaveable { mutableStateOf(false) }
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        val ctx = androidx.compose.ui.platform.LocalContext.current
        fun copy(label: String, v: String) {
            clip.setText(androidx.compose.ui.text.AnnotatedString(v))
            android.widget.Toast.makeText(ctx, "$label copié : $v", android.widget.Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(14.dp))
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.linearGradient(listOf(Color(0x66FFC94D), Color(0x40FF4FA3), Color(0x338B5CFF))))
                .border(1.dp, Brush.linearGradient(Vivid.Rainbow), shape)
                .padding(22.dp),
        ) {
            Text("📅  Cette semaine · ${Money.eur(a.weekBudget, 0)}", style = MaterialTheme.typography.titleMedium, color = Xp.Gold)
            val mondays = be.maikel.xplorer.core.mondaysInMonth(be.maikel.xplorer.domain.DcaJournal.weekStart(System.currentTimeMillis()) + 43_200_000L)
            Muted("${Money.eur(a.snap.settings.monthlyBudget, 0)} par mois ÷ $mondays lundis", small = false)
            if (a.weekBought >= 1) {
                Spacer(Modifier.height(8.dp))
                GradientBar(a.weekBought / a.weekBudget.coerceAtLeast(1.0), listOf(Vivid.Bulls.copy(alpha = 0.7f), Vivid.Bulls))
                Spacer(Modifier.height(4.dp))
                Text("Déjà acheté ${Money.eur(a.weekBought, 0)} · reste ${Money.eur((a.weekBudget - a.weekBought).coerceAtLeast(0.0), 0)}", style = MaterialTheme.typography.labelLarge, color = Vivid.Bulls)
            }
            Spacer(Modifier.height(10.dp))
            if (buys.isEmpty()) {
                Text(if (a.weekBought >= 1) "Semaine bouclée ✅" else "Rien à acheter.", style = MaterialTheme.typography.titleLarge, color = Xp.Ice)
                Spacer(Modifier.height(4.dp))
                Text(a.plan.headline, style = MaterialTheme.typography.bodyLarge, color = Xp.Ice)
            } else {
                Text(if (a.weekBought >= 1) "Il te reste à acheter :" else "Achète :", style = MaterialTheme.typography.bodyLarge, color = Xp.Slate)
                buys.forEach { b ->
                    val d = a.arena[b.symbol]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onAsset(b.symbol) }.padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoinBadge(b.symbol, 30); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(coinName(b.symbol), style = MaterialTheme.typography.titleMedium, color = Xp.Ice)
                            // Pourquoi ce montant : la cible contre ce que tu as aujourd'hui.
                            val target = a.snap.settings.targets[b.symbol] ?: 0.0
                            val now = if (l.cryptoEur > 0) (held[b.symbol] ?: 0.0) / l.cryptoEur else 0.0
                            val gap = now - target
                            Text(
                                "Cible ${Money.pct(target)} · tu as ${Money.pct(now)}" + when {
                                    gap < -0.01 -> " → on rattrape"
                                    gap > 0.01 -> " → on freine"
                                    else -> " → pile dedans"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = when { gap < -0.01 -> Vivid.Sky; gap > 0.01 -> Vivid.Sun; else -> Xp.Slate },
                            )
                            if (d != null && (d.score > 0.15 || d.score < -0.15)) Text(
                                if (d.score > 0) "Bulls" else "Bears",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (d.score > 0) Vivid.Bulls else Vivid.Bears,
                            )
                        }
                        Text(Money.eur(b.eur, 0), style = MaterialTheme.typography.titleLarge, color = Xp.Ice)
                        Text("  ›", style = MaterialTheme.typography.titleLarge, color = Xp.Slate)
                    }
                    // Mode achat : prix limite et quantité à taper sur Coinbase Advanced (touche pour copier).
                    if (buyMode) be.maikel.xplorer.domain.LimitOrder.suggest(b.eur, a.snap.prices[b.symbol] ?: Double.NaN, a.feeRate)?.let { sg ->
                        Row(Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))
                                    .clickable { copy("Prix limite", sg.priceText) }.padding(8.dp),
                            ) {
                                Muted("Prix limite 📋")
                                Text("${sg.priceText} €", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            }
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))
                                    .clickable { copy("Quantité", sg.qtyText) }.padding(8.dp),
                            ) {
                                Muted("Quantité 📋")
                                Text("${sg.qtyText} ${b.symbol}", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (buyMode) Vivid.Sky.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                        .clickable { buyMode = !buyMode }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🛒", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (buyMode) "Mode achat activé" else "Mode achat : prêt à copier", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Muted(
                            if (buyMode) "Sur Coinbase Advanced : Acheter › Limite › colle le prix et la quantité. Frais estimés d'après tes vrais ordres : ≈ ${Money.eur(buys.sumOf { it.eur } * a.feeRate, 2)} pour la semaine."
                            else "Prix et quantité prêts à copier pour chaque crypto.",
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Les montants te ramènent pile à ta répartition. Touche une crypto pour son analyse.", style = MaterialTheme.typography.bodyMedium, color = Xp.Slate)
                // Rappel doux : pas d'alarme, juste ce qu'il faut virer et une astuce.
                if (hasKey && l.cashEur + 0.5 < a.plan.lines.sumOf { it.eur }) {
                    val need = a.plan.lines.sumOf { it.eur } - l.cashEur
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Vivid.Sky.copy(alpha = 0.12f))
                            .border(1.dp, Vivid.Sky.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🏦", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pense à virer ${Money.eur(need, 0)} sur Coinbase", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Muted("Astuce : un virement permanent chaque lundi depuis ta banque, et tu n'y penses plus.")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onDone(buys.map { it.symbol to it.eur }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Xp.Gold, contentColor = Color(0xFF231700)),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("C'est fait, j'ai acheté", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(6.dp))
                Muted("Tu achètes toi-même sur Coinbase. L'app ne touche jamais à ton argent.")
            }
        }
    }

    // Journal : semaines faites, montants réels, frais.
    if (a.journal != null) item { journalCard(a) }

    // 3. Le trésor crypto et ses paliers.
    a.milestones?.let { m -> item { wealthCard(m) } }

    // 4. L'arène : bœufs contre ours, crypto par crypto.
    if (a.arena.isNotEmpty()) item { arenaCard(a, onAsset) }

    // 5. Mes cryptos : chiffres, palier et analyse technique (touche une carte pour tout voir).
    if (a.milestones != null) item { cryptoCards(a, onAsset) }

    // 6. L'arène a-t-elle raison ? Test honnête sur le passé.
    if (a.arenaBacktest != null) item { arenaBacktestCard(a) }

    // 6. Pour aller plus loin.
    item {
        Spacer(Modifier.height(14.dp))
        Glass(onClick = onDetails) {
            Text("🔍  Voir les détails", style = MaterialTheme.typography.titleMedium, color = Xp.Cyan)
            Muted("Graphiques, indicateurs, budget libre, historique des conseils.", small = false)
        }
        Spacer(Modifier.height(10.dp))
        Muted("Conseils seulement, jamais de garantie de gain.", Modifier.padding(horizontal = 8.dp), small = false)
    }
}
