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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun hello(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bonjour Maïkel"
    in 12..17 -> "Bon après-midi Maïkel"
    else -> "Bonsoir Maïkel"
}

/**
 * Accueil Xcap : ton argent, la consigne de la semaine, l'arène des agents.
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
        Glass(padding = 20, onClick = if (hasKey) ({ detail = !detail }) else null) {
            if (hasKey) {
                // Juste les faits : ce que tu as mis en DCA, et ce que tu possèdes aujourd'hui.
                Muted("Mon DCA crypto", small = false)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Muted("J'ai mis")
                        Text(Money.eur(a.capital, 0), style = BigNumber, color = Xp.Ice)
                    }
                    Box(Modifier.padding(bottom = 6.dp).width(1.dp).height(40.dp).background(Xp.Edge2))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Muted("Je possède")
                        Text(Money.eur(l.totalEur, 0), style = BigNumber, color = Xp.Cyan)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(if (detail) "Masquer le détail  ▴" else "D'où vient « J'ai mis » ?  ▾", style = MaterialTheme.typography.labelLarge, color = Xp.Cyan)
                if (detail) {
                    Spacer(Modifier.height(8.dp))
                    a.capitalLines.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.label, style = MaterialTheme.typography.bodyMedium, color = Xp.Ice)
                            }
                            Text(Money.eur(c.eur, 2), style = MaterialTheme.typography.titleSmall, color = Vivid.Sky)
                        }
                    }
                    if (a.capitalLines.isEmpty()) Muted("Aucun virement lu pour l'instant.")
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Total mis pour le DCA", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                        Text(Money.eur(a.capital, 2), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    if (a.capitalManual) Muted("Montant corrigé à la main dans les réglages.")
                    Spacer(Modifier.height(4.dp))
                    Muted("« J'ai mis » = l'argent que tu as mis pour ton DCA, lu sur tes virements Coinbase (clé en lecture seule). L'argent que tu as récupéré sur ta banque est retiré du calcul, comme si tu ne l'avais jamais mis.")
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Banner("Connecte ton compte Coinbase (lecture seule) pour voir ton vrai argent. Touche ici.", error = false) { onSettings() }
            }
        }
    }

    // Ne panique pas : grosse baisse du Bitcoin.
    a.guardian?.let { g -> item { guardianCard(g) } } ?: a.btcWeek?.takeIf { it <= -0.15 }?.let { d -> item { calmBanner(d) } }

    // 2. La consigne de la semaine.
    // Ordre : de ta plus grosse crypto à la plus petite.
    val held = l.positions.associate { it.symbol to it.value }
    // Règle de Maïkel : aucune crypto possédée n'est jamais masquée (même sans achat cette semaine).
    val planBySym = a.plan.lines.associateBy { it.symbol }
    val rows = (a.plan.lines.map { it.symbol } + l.positions.filter { it.value > 0.0 }.map { it.symbol })
        .distinct()
        .map { sym -> HomeRow(sym, planBySym[sym]?.eur ?: 0.0) }
        .sortedByDescending { held[it.symbol] ?: 0.0 }
    val buys = a.plan.lines.filter { it.eur > 0 }.sortedByDescending { held[it.symbol] ?: 0.0 }
    item {
        Spacer(Modifier.height(14.dp))
        // Carte de la semaine : fin contour dégradé or → rose → violet, fond nuit uni.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF2B544), Color(0xFFE0607E), Color(0xFF7C5CFF))))
                .padding(2.dp),
        ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(WeekInk)
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cette semaine", style = MaterialTheme.typography.titleLarge, color = Xp.Gold)
                    Muted(
                        if (a.mondayRank >= 5) "5e lundi du mois : pause, tes ${Money.eur(a.snap.settings.monthlyBudget, 0)} sont atteints"
                        else "${Money.eur(a.snap.settings.weeklyBudget, 0)} par semaine · ${Money.eur(a.snap.settings.monthlyBudget, 0)} max par mois",
                    )
                }
                Text(Money.eur(a.weekBudget, 0), style = BigNumber.copy(fontSize = 30.sp), color = Xp.Gold)
            }
            if (a.weekBought >= 1) {
                Spacer(Modifier.height(8.dp))
                GradientBar(a.weekBought / a.weekBudget.coerceAtLeast(1.0), listOf(Vivid.Bulls.copy(alpha = 0.7f), Vivid.Bulls))
                Spacer(Modifier.height(4.dp))
                Text("Déjà acheté ${Money.eur(a.weekBought, 0)} · reste ${Money.eur((a.weekBudget - a.weekBought).coerceAtLeast(0.0), 0)}", style = MaterialTheme.typography.labelLarge, color = Vivid.Bulls)
            }
            Spacer(Modifier.height(10.dp))
            if (buys.isEmpty()) {
                if (a.mondayRank >= 5 && a.weekBought < 1) {
                    Text("Semaine de pause", style = MaterialTheme.typography.titleLarge, color = Xp.Ice)
                    Spacer(Modifier.height(4.dp))
                    Text("Tu as déjà mis tes 4 × ${Money.eur(a.snap.settings.weeklyBudget, 0)} ce mois-ci. On reprend lundi prochain.", style = MaterialTheme.typography.bodyLarge, color = Xp.Ice)
                } else {
                    Text(if (a.weekBought >= 1) "Semaine bouclée ✅" else "Rien à acheter.", style = MaterialTheme.typography.titleLarge, color = Xp.Ice)
                    Spacer(Modifier.height(4.dp))
                    Text(a.plan.headline, style = MaterialTheme.typography.bodyLarge, color = Xp.Ice)
                }
                Spacer(Modifier.height(10.dp))
            } else {
                if (a.weekBought >= 1) Muted("Il te reste à acheter :", Modifier.padding(start = 4.dp, bottom = 4.dp), small = false)
                a.dipHunter?.let { h -> hunterIntro(h) }
            }
                rows.forEach { b ->
                    val d = a.arena[b.symbol]
                    // Pourquoi ce montant : la cible contre ce que tu as aujourd'hui.
                    val target = a.snap.settings.targets[b.symbol] ?: 0.0
                    val now = if (l.cryptoEur > 0) (held[b.symbol] ?: 0.0) / l.cryptoEur else 0.0
                    val gap = now - target
                    val (status, fg, bg) = when {
                        target <= 0.0 -> Triple("hors plan", Color(0xFFB7C2D6), Color(0xFF1B2233))
                        gap < -0.01 -> Triple("on rattrape", Xp.Cyan, Color(0xFF0E2A33))
                        gap > 0.01 -> Triple("on freine", Xp.Gold, Color(0xFF2A2210))
                        else -> Triple("pile dedans", Color(0xFFB7C2D6), Color(0xFF1B2233))
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onAsset(b.symbol) }.padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SolidCoin(b.symbol, 42); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(coinName(b.symbol), style = MaterialTheme.typography.titleMedium, color = Xp.Ice, maxLines = 1)
                                Spacer(Modifier.width(8.dp))
                                Pill(status, fg, bg)
                            }
                            Spacer(Modifier.height(7.dp))
                            TargetBar(now, target, fg)
                            Spacer(Modifier.height(5.dp))
                            Row {
                                Text("tu as ${Money.pct(now)} · cible ${Money.pct(target)}", style = MaterialTheme.typography.labelSmall, color = Xp.Slate)
                                if (d != null && (d.score > 0.15 || d.score < -0.15)) Text(
                                    if (d.score > 0) " · Bulls" else " · Bears",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (d.score > 0) Vivid.Bulls else Vivid.Bears,
                                )
                            }
                            if (b.eur > 0) a.dipHunter?.line(b.symbol)?.let { h -> dipLine(h) }
                            else Text(
                                if (target <= 0.0) "Hors de ta répartition : suivie, pas d'achat."
                                else if (gap > 0.01) "Au-dessus de sa cible : pas d'achat cette semaine."
                                else "Pile à sa cible : pas d'achat cette semaine.",
                                style = MaterialTheme.typography.labelLarge, color = Xp.Slate,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.eur(b.eur, 0), style = MaterialTheme.typography.titleLarge, color = if (b.eur > 0) Xp.Ice else Xp.Slate)
                            Text(if (b.eur > 0) "cette semaine" else "pas d'achat", style = MaterialTheme.typography.labelSmall, color = Xp.Slate)
                        }
                        Text("  ›", style = MaterialTheme.typography.titleLarge, color = Color(0xFF5A6884))
                    }
                }
                if (buys.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
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
                    colors = ButtonDefaults.buttonColors(containerColor = Xp.Cyan, contentColor = Color(0xFF02161D)),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("C'est fait, j'ai acheté", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(6.dp))
                Muted("Tu achètes toi-même sur Coinbase. L'app ne touche jamais à ton argent.", Modifier.padding(horizontal = 4.dp))
                }
        }
        }
    }

    // Le Miroir, la Sentinelle GEOD et Cap 2030.
    a.rebalance?.let { r -> item { rebalanceCard(r) } }
    a.mirror?.let { m -> item { mirrorCard(m) } }
    a.sentinel?.let { s -> item { sentinelCard(s) } }
    a.cap?.let { c -> item { capCard(c) } }

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


/** Fond de la carte de la semaine. */
private val WeekInk = Color(0xFF0D111D)

/** Grands montants de l'accueil. */
private val BigNumber = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-1).sp)

/** Pastille ronde aux vraies couleurs de la crypto. */
@Composable
private fun SolidCoin(sym: String, size: Int) {
    val c = coinColor(sym)
    val fg = if (c.luminance() > 0.4f) Color(0xFF0B0F1A) else Color.White
    Box(Modifier.size(size.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
        Text(sym.take(4), style = MaterialTheme.typography.labelSmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 10.sp), color = fg)
    }
}

/** Petit badge d'état : on freine, on rattrape, pile dedans. */
@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Mini-barre : ta part actuelle, et un repère blanc pour la cible. */
@Composable
private fun TargetBar(now: Double, target: Double, color: Color) {
    val scale = maxOf(0.6, now * 1.1, target * 1.1)
    BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1B2740)))
        Box(Modifier.fillMaxWidth((now / scale).toFloat().coerceIn(0.02f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        val x = maxWidth * (target / scale).toFloat().coerceIn(0f, 1f)
        Box(Modifier.offset(x = (x - 1.dp).coerceAtLeast(0.dp)).width(2.dp).height(10.dp).clip(RoundedCornerShape(1.dp)).background(Xp.Ice))
    }
}


/** Une ligne de la carte de la semaine : chaque crypto possédée ou visée, achat éventuellement nul. */
private data class HomeRow(val symbol: String, val eur: Double)
