package be.maikel.xplorer.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Assistant ordre limite : un prix légèrement sous le cours pour être « maker » sur Coinbase Advanced
 * (frais ≈ 0,6 % au lieu de ≈ 1,2 % au marché, barème de base estimé).
 */
object LimitOrder {
    /** Écart sous le cours actuel : assez petit pour être exécuté vite, assez grand pour ne pas croiser le carnet. */
    const val DISCOUNT = 0.002
    const val FEE_MARKET = 0.012
    const val FEE_LIMIT = 0.006

    data class Suggestion(val price: Double, val qty: Double, val priceText: String, val qtyText: String, val saving: Double)

    fun suggest(eur: Double, price: Double): Suggestion? {
        if (!(eur > 0) || !(price > 0)) return null
        val dec = Money.priceDecimals(price)
        val lp = BigDecimal(price * (1 - DISCOUNT)).setScale(dec, RoundingMode.DOWN)
        if (lp.signum() <= 0) return null
        // Montant net de frais, quantité arrondie vers le bas pour ne jamais dépasser le budget.
        val q = BigDecimal(eur * (1 - FEE_LIMIT)).divide(lp, 8, RoundingMode.DOWN).stripTrailingZeros()
        return Suggestion(
            lp.toDouble(), q.toDouble(),
            lp.toPlainString().replace('.', ','),
            q.toPlainString().replace('.', ','),
            eur * (FEE_MARKET - FEE_LIMIT),
        )
    }
}
