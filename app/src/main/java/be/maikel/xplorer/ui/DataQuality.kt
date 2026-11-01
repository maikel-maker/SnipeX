package be.maikel.xplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Un message technique traduit en clair. [matters] = peut fausser un chiffre que tu regardes. */
private data class Plain(val emoji: String, val text: String, val matters: Boolean)

private fun plain(raw: String): Plain {
    Regex("""^(\d+) opération\(s\) d'un type non reconnu""").find(raw)?.let {
        return Plain("ℹ️", "${it.groupValues[1]} opérations Coinbase d'un genre que l'app ne classe pas encore (récompenses, conversions…). Elles sont comptées au prix donné par Coinbase : tes montants restent justes.", false)
    }
    Regex("""^(\w+) : l'historique compte plus que ton solde \(([^ ]+) contre ([^)]+)\)""").find(raw)?.let {
        val (sym, hist, real) = it.destructured
        return Plain("⚠️", "$sym : ton solde réel est de $real, mais l'historique en compte $hist. Une vente ou un envoi n'a pas été bien lu. Ton solde et sa valeur sont justes (ils viennent directement de Coinbase), seul ton prix moyen d'achat de $sym peut être un peu faux.", true)
    }
    Regex("""^(\w+) : (.+) détenu\(s\) sans historique d'achat""").find(raw)?.let {
        val (sym, q) = it.destructured
        return Plain("⚠️", "$sym : $q sans achat retrouvé (cadeau, récompense ou ancien transfert). Le prix moyen de $sym ne tient compte que du reste.", true)
    }
    Regex("""^(\w+) : prix introuvable""").find(raw)?.let {
        return Plain("⚠️", "${it.groupValues[1]} : pas de prix trouvé, sa valeur n'est pas comptée dans « Je possède ».", true)
    }
    if (raw.contains("comptes Coinbase lus sur")) return Plain("ℹ️", "Les comptes Coinbase vides sont ignorés. Normal.", false)
    Regex("""^Pas d'historique de prix pour (.+) :""").find(raw)?.let {
        return Plain("ℹ️", "${it.groupValues[1]} : pas d'historique de prix, cette crypto n'apparaît pas dans les courbes passées (souvent un reste minuscule).", false)
    }
    Regex("""^Transferts de cryptos depuis ou vers l'extérieur : ([^(]+)""").find(raw)?.let {
        return Plain("ℹ️", "Depuis l'ouverture de ton compte, ${it.groupValues[1].trim()} de cryptos sont arrivés ou partis par envoi. Ce ne sont ni des gains ni des pertes, l'app les met de côté.", false)
    }
    if (raw.startsWith("Capital apporté corrigé à la main")) return Plain("✏️", raw, false)
    if (raw.contains("jour(s) incohérent(s)")) return Plain("ℹ️", "Quelques jours de l'historique ont des trous : ils sont ignorés dans les courbes de performance.", false)
    return Plain("ℹ️", raw, false)
}

/** « Fiabilité des chiffres » : un verdict simple, les points qui comptent, le reste replié. */
@Composable
fun dataQualityCard(issues: List<String>) {
    val all = issues.map { plain(it) }
    val important = all.filter { it.matters }
    val minor = all.filter { !it.matters }
    var open by rememberSaveable { mutableStateOf(false) }
    SectionTitle("🔎 Fiabilité des chiffres")
    Glass {
        val ok = important.isEmpty()
        val c = if (ok) Feu.Vert else Feu.Orange
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.14f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (ok) "✅" else "⚠️", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (ok) "Tes chiffres sont fiables" else "${important.size} point(s) à savoir", style = MaterialTheme.typography.titleSmall, color = c, fontWeight = FontWeight.Bold)
                Muted("Soldes, « J'ai mis », « Je possède » et le plan de la semaine viennent directement de Coinbase.")
            }
        }
        important.forEach { p ->
            Spacer(Modifier.height(10.dp))
            Text("${p.emoji}  ${p.text}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        if (minor.isNotEmpty()) {
            TextButton(onClick = { open = !open }) {
                Text(if (open) "Masquer les petits détails ▲" else "Petits détails sans importance (${minor.size}) ▼", color = Xp.Cyan)
            }
            if (open) minor.forEach { p -> Text("${p.emoji}  ${p.text}", style = MaterialTheme.typography.bodySmall, color = Xp.Slate, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}
