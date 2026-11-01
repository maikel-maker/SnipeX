package be.maikel.xplorer.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Assistant ordre limite : un prix légèrement sous le cours, et la quantité qui tient dans le montant
 * en tenant compte de tes frais réels (taux moyen lu sur tes ordres Coinbase).
 */
object LimitOrder {
    /** Écart sous le cours actuel : assez petit pour être exécuté vite, assez grand pour ne pas croiser le carnet. */
    const val DISCOUNT = 0.002
    /** Taux par défaut si aucun ordre n'a encore été lu (barème maker de base estimé). */
    const val DEFAULT_FEE = 0.006

    data class Suggestion(val price: Double, val qty: Double, val priceText: String, val qtyText: String)

    fun suggest(eur: Double, price: Double, feeRate: Double = DEFAULT_FEE): Suggestion? {
        if (!(eur > 0) || !(price > 0)) return null
        val dec = Money.priceDecimals(price)
        val lp = BigDecimal(price * (1 - DISCOUNT)).setScale(dec, RoundingMode.DOWN)
        if (lp.signum() <= 0) return null
        // Montant net de frais, quantité arrondie vers le bas pour ne jamais dépasser le budget.
        val fee = if (feeRate > 0 && feeRate < 0.05) feeRate else DEFAULT_FEE
        val q = BigDecimal(eur * (1 - fee)).divide(lp, 8, RoundingMode.DOWN).stripTrailingZeros()
        return Suggestion(
            lp.toDouble(), q.toDouble(),
            lp.toPlainString().replace('.', ','),
            q.toPlainString().replace('.', ','),
        )
    }
}
