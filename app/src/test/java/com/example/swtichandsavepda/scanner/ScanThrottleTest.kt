package com.example.swtichandsavepda.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanThrottleTest {

    /** Advanceable clock so the tests never depend on wall time. */
    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
        fun advance(millis: Long) {
            now += millis
        }
    }

    @Test
    fun `first sighting of a barcode is accepted`() {
        val throttle = ScanThrottle(rescanDelayMillis = 2_000, clock = FakeClock())

        assertTrue(throttle.shouldAccept("5012345678900"))
    }

    @Test
    fun `same barcode within the delay window is suppressed`() {
        val clock = FakeClock()
        val throttle = ScanThrottle(rescanDelayMillis = 2_000, clock = clock)

        throttle.shouldAccept("5012345678900")
        clock.advance(500)

        assertFalse(throttle.shouldAccept("5012345678900"))
    }

    @Test
    fun `same barcode is accepted again once the delay has elapsed`() {
        val clock = FakeClock()
        val throttle = ScanThrottle(rescanDelayMillis = 2_000, clock = clock)

        throttle.shouldAccept("5012345678900")
        clock.advance(2_000)

        assertTrue(throttle.shouldAccept("5012345678900"))
    }

    @Test
    fun `a different barcode is accepted immediately`() {
        val clock = FakeClock()
        val throttle = ScanThrottle(rescanDelayMillis = 2_000, clock = clock)

        throttle.shouldAccept("5012345678900")
        clock.advance(10)

        // Scanning a second label right after the first must not be blocked.
        assertTrue(throttle.shouldAccept("5099876543210"))
    }

    @Test
    fun `suppression window restarts after an accepted repeat`() {
        val clock = FakeClock()
        val throttle = ScanThrottle(rescanDelayMillis = 2_000, clock = clock)

        throttle.shouldAccept("5012345678900")
        clock.advance(2_000)
        throttle.shouldAccept("5012345678900")
        clock.advance(500)

        assertFalse(throttle.shouldAccept("5012345678900"))
    }

    @Test
    fun `reset clears the suppression window`() {
        val clock = FakeClock()
        val throttle = ScanThrottle(rescanDelayMillis = 2_000, clock = clock)

        throttle.shouldAccept("5012345678900")
        throttle.reset()

        assertTrue(throttle.shouldAccept("5012345678900"))
    }
}
