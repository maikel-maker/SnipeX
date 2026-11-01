package be.maikel.xplorer

import be.maikel.xplorer.domain.LimitOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitOrderTest {
    @Test fun btcPriceIsSlightlyBelowAndQtyFitsBudget() {
        val s = LimitOrder.suggest(125.0, 70_000.0)!!
        assertEquals(69_860.0, s.price, 0.0)          // −0,2 %, arrondi à l'euro
        assertTrue(s.qty * s.price <= 125.0)
        assertEquals("69860", s.priceText)
        assertEquals(125.0 * 0.006, s.saving, 1e-9)
    }

    @Test fun smallPriceKeepsDecimals() {
        val s = LimitOrder.suggest(37.0, 0.2175)!!
        assertEquals("0,217", s.priceText.take(5))
        assertTrue(s.qty * s.price <= 37.0)
    }

    @Test fun nothingWithoutPrice() {
        assertNull(LimitOrder.suggest(50.0, Double.NaN))
        assertNull(LimitOrder.suggest(0.0, 100.0))
    }
}
