package be.maikel.xplorer

import be.maikel.xplorer.domain.Budget
import be.maikel.xplorer.domain.Candle
import be.maikel.xplorer.domain.Cap
import be.maikel.xplorer.domain.DcaJournal
import be.maikel.xplorer.domain.DipHunter
import be.maikel.xplorer.domain.Guardian
import be.maikel.xplorer.domain.Mirror
import be.maikel.xplorer.domain.Sentinel
import be.maikel.xplorer.domain.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ConceptsTest {
    private val day = 86_400_000L
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun monday(y: Int, m: Int, d: Int) = DcaJournal.weekStart(at(y, m, d))

    // ── Budget : 250 € les 4 premiers lundis, pause le 5e ──

    @Test fun novemberHasAPauseOnTheFifthMonday() {
        // novembre 2026 : lundis 2, 9, 16, 23, 30
        assertEquals(250.0, Budget.week(250.0, 1000.0, monday(2026, 11, 2)), 0.0)
        assertEquals(250.0, Budget.week(250.0, 1000.0, monday(2026, 11, 23)), 0.0)
        assertEquals(5, Budget.mondayRank(monday(2026, 11, 30)))
        assertEquals(0.0, Budget.week(250.0, 1000.0, monday(2026, 11, 30)), 0.0)
    }

    @Test fun neverMoreThanAThousandAMonth() {
        for (m in 1..12) {
            val c = Calendar.getInstance().apply { clear(); set(2027, m - 1, 1, 12, 0) }
            var total = 0.0
            while (c.get(Calendar.MONTH) == m - 1) {
                if (c.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) total += Budget.week(250.0, 1000.0, DcaJournal.weekStart(c.timeInMillis))
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
            assertEquals("mois $m", 1000.0, total, 0.0)
        }
    }

    // ── Chasseur de creux ──

    @Test fun fortyPercentNowTheRestWaitsForADip() {
        val tue = at(2026, 11, 10)
        val h = DipHunter.compute(mapOf("BTC" to 100.0, "ETH" to 150.0), 250.0, 0.0, mapOf("BTC" to 100.0, "ETH" to 100.0), mapOf("BTC" to 99.0, "ETH" to 96.0), tue)
        val btc = h.line("BTC")!!
        val eth = h.line("ETH")!!
        assertEquals(40.0, btc.nowEur, 0.0)
        assertEquals(60.0, btc.laterEur, 0.0)
        assertEquals(DipHunter.State.WAIT, btc.state)
        assertEquals(DipHunter.State.DIP, eth.state)   // −4 % depuis lundi
        assertEquals(1, h.ready.size)
    }

    @Test fun fridayBuysTheRest() {
        val fri = at(2026, 11, 13)
        val h = DipHunter.compute(mapOf("BTC" to 250.0), 250.0, 0.0, mapOf("BTC" to 100.0), mapOf("BTC" to 101.0), fri)
        assertEquals(DipHunter.State.DEADLINE, h.line("BTC")!!.state)
    }

    @Test fun firstTrancheAlreadyBoughtMeansEverythingWaits() {
        val h = DipHunter.compute(mapOf("BTC" to 150.0), 250.0, 100.0, emptyMap(), emptyMap(), at(2026, 11, 10))
        assertEquals(0.0, h.line("BTC")!!.nowEur, 0.0)
        assertEquals(150.0, h.line("BTC")!!.laterEur, 0.0)
    }

    // ── Miroir ──

    private fun candles(start: Long, closes: List<Double>) = closes.mapIndexed { i, c -> Candle(start + i * day, c, c, c, c, 1.0) }

    @Test fun mirrorReplaysTheSameMoney() {
        val t0 = at(2026, 10, 1, 0)
        val daily = mapOf(
            "BTC" to candles(t0, listOf(100.0, 100.0, 200.0)),
            "ETH" to candles(t0, listOf(10.0, 10.0, 10.0)),
        )
        val trades = listOf(Trade(t0 + 2 * day + 1, "ETH", true, 10.0, 10.0, 100.0, 0.0, "buy", "a", null))
        val r = Mirror.compute(trades, 0L, daily, mapOf("BTC" to 200.0, "ETH" to 10.0), mapOf("BTC" to 0.5, "ETH" to 0.5))!!
        assertEquals(100.0, r.invested, 1e-9)
        assertEquals(100.0, r.mine, 1e-9)            // ETH n'a pas bougé
        assertEquals(100.0, r.allBtc!!, 1e-9)         // acheté à 200, vaut 200
        assertEquals(100.0, r.fixedSplit!!, 1e-9)
        assertEquals(4, r.ranking.size)
    }

    @Test fun mirrorIsNullWithoutBuys() {
        assertNull(Mirror.compute(emptyList(), 0L, emptyMap(), emptyMap(), emptyMap()))
    }

    // ── Cap 2030 ──

    @Test fun capProjectsMoreWithGrowth() {
        val r = Cap.project(at(2026, 11, 5), 600.0, 1000.0, mapOf("ETH" to 0.05), mapOf("BTC" to 90000.0, "ETH" to 3000.0), mapOf("BTC" to 0.5, "ETH" to 0.25))
        assertEquals(50, r.months)                    // novembre 2026 → décembre 2030
        val (flat, mid, high) = r.scenarios
        assertEquals(600.0 + 50 * 1000.0, flat.finalValue, 1e-6)
        assertTrue(mid.finalValue > flat.finalValue && high.finalValue > mid.finalValue)
        assertNotNull(r.goals.first { it.label.contains("ETH") }.months)
    }

    // ── Sentinelle GEOD ──

    @Test fun sentinelLevels() {
        val s = Sentinel.compute(1.26, 0.21, 20.0, 1000.0)!!
        assertEquals(6.0, s.multiple, 1e-9)
        assertEquals(10.0, s.next)
        assertEquals(5, Sentinel.levelReached(s.multiple, 20.0))
        assertEquals(0, Sentinel.levelReached(1.0, 20.0))
        assertEquals(20, Sentinel.levelReached(25.0, 20.0))
        assertEquals(4200.0, s.valueAtTarget, 1e-6)
    }

    // ── Gardien ──

    @Test fun guardianOnlyWakesUpOnBigDrops() {
        assertNull(Guardian.compute(-0.05, emptyList(), emptyMap(), emptyMap()))
        assertNotNull(Guardian.compute(-0.12, emptyList(), emptyMap(), emptyMap()))
    }
}
