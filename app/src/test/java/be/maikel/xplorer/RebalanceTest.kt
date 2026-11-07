package be.maikel.xplorer

import be.maikel.xplorer.domain.Rebalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RebalanceTest {
    private val targets = mapOf("BTC" to 0.50, "ETH" to 0.25, "GEOD" to 0.15, "LINK" to 0.05, "TAO" to 0.05)
    private val pairs = setOf("BTC-EUR", "ETH-EUR", "LINK-EUR", "TAO-USD", "GEOD-USD", "ETH-BTC", "LINK-BTC")

    @Test fun nothingWhenCloseToTargets() {
        val v = mapOf("BTC" to 530.0, "ETH" to 240.0, "GEOD" to 140.0, "LINK" to 45.0, "TAO" to 45.0)
        assertNull(Rebalance.compute(v, targets, pairs))
    }

    @Test fun btcFarAboveTargetGoesToLaggardsViaDirectPair() {
        // BTC 70 % (cible 50 %), ETH 10 % (cible 25 %)
        val v = mapOf("BTC" to 700.0, "ETH" to 100.0, "GEOD" to 150.0, "LINK" to 25.0, "TAO" to 25.0)
        val s = Rebalance.compute(v, targets, pairs)
        assertNotNull(s)
        val first = s!!.moves.first()
        assertEquals("BTC", first.from)
        assertEquals("ETH", first.to)
        assertEquals("ETH-BTC", first.pair)
        assertEquals(150.0, first.eur, 0.01)                 // ETH remonte pile à 25 %
        assertTrue(s.moves.sumOf { it.eur } <= 200.0 + 1e-9)  // jamais plus que l'excédent de BTC
        assertTrue(s.moves.none { it.to == "EUR" })            // on reste investi
    }

    @Test fun lockedGeodIsNeverSold() {
        val v = mapOf("BTC" to 400.0, "ETH" to 200.0, "GEOD" to 350.0, "LINK" to 25.0, "TAO" to 25.0)
        assertNull(Rebalance.compute(v, targets, pairs, locked = setOf("GEOD")))
        assertNotNull(Rebalance.compute(v, targets, pairs))
    }

    @Test fun twoOrdersWhenNoDirectPair() {
        val v = mapOf("BTC" to 800.0, "ETH" to 250.0, "GEOD" to 150.0, "LINK" to 50.0, "TAO" to 0.0)
        val m = Rebalance.compute(v, targets, pairs)!!.moves.first { it.to == "TAO" }
        assertNull(m.pair)
        assertTrue(m.instruction.contains("2 ordres"))
        assertTrue(m.instruction.contains("TAO-USD"))
    }
}
