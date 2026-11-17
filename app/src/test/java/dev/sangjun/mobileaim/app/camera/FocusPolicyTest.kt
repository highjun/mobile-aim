package dev.sangjun.mobileaim.app.camera

import dev.sangjun.mobileaim.utils.Point2Px
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {

    private val board = Point2Px(300f, 200f)

    private fun detected(center: Point2Px? = board) = FocusInput(
        detected = true,
        lostLong = false,
        boardCenterPx = center,
        frameWidth = FRAME_WIDTH,
        frameHeight = FRAME_HEIGHT,
    )

    private fun missed(lostLong: Boolean, center: Point2Px? = null) = FocusInput(
        detected = false,
        lostLong = lostLong,
        boardCenterPx = center,
        frameWidth = FRAME_WIDTH,
        frameHeight = FRAME_HEIGHT,
    )

    /** Drives [policy] through a stable detection until it locks, returning the lock command. */
    private fun lockOn(policy: FocusPolicy, startMillis: Long = 0L): FocusCommand.Acquire {
        assertNull(policy.onInput(detected(), startMillis))
        val command = policy.onInput(detected(), startMillis + FocusPolicy.MIN_STABLE_MILLIS)
        assertTrue("expected a lock, got $command", command is FocusCommand.Acquire && command.lock)
        return command as FocusCommand.Acquire
    }

    @Test
    fun `locks on the board once detection has held steady`() {
        val policy = FocusPolicy()
        val command = lockOn(policy)
        assertEquals(board, command.point)
    }

    @Test
    fun `does not lock on a single lucky frame`() {
        val policy = FocusPolicy()
        assertNull(policy.onInput(detected(), 0L))
        assertNull(policy.onInput(detected(), FocusPolicy.MIN_STABLE_MILLIS - 1))
    }

    @Test
    fun `restarts the stability window after a dropped frame`() {
        val policy = FocusPolicy()
        assertNull(policy.onInput(detected(), 0L))
        policy.onInput(missed(lostLong = false), 100L)
        // The clock is already past MIN_STABLE_MILLIS, but the run of detections restarted.
        assertNull(policy.onInput(detected(), 200L))
        assertNull(policy.onInput(detected(), 200L + FocusPolicy.MIN_STABLE_MILLIS - 1))
    }

    @Test
    fun `stays silent while locked and detecting`() {
        val policy = FocusPolicy()
        lockOn(policy)
        // This is the whole point of the policy: no further focus requests, so no re-scan, no hunt.
        var now = FocusPolicy.MIN_STABLE_MILLIS
        repeat(200) {
            now += 33L
            assertNull(policy.onInput(detected(), now))
        }
    }

    @Test
    fun `holds focus through a brief miss`() {
        val policy = FocusPolicy()
        lockOn(policy)
        var now = FocusPolicy.MIN_STABLE_MILLIS
        repeat(60) {
            now += 33L
            assertNull(policy.onInput(missed(lostLong = false), now))
        }
    }

    @Test
    fun `releases once the board is genuinely lost, then locks again on re-detection`() {
        val policy = FocusPolicy()
        lockOn(policy)
        val lostAt = FocusPolicy.MIN_STABLE_MILLIS + 3_000L
        assertEquals(FocusCommand.Release, policy.onInput(missed(lostLong = true), lostAt))
        // Released only once — a still-lost board doesn't keep emitting releases.
        assertTrue(policy.onInput(missed(lostLong = true), lostAt + 33L) !is FocusCommand.Release)

        val reacquireAt = lostAt + FocusPolicy.SEARCH_RETRY_MILLIS + 10_000L
        assertNull(policy.onInput(detected(), reacquireAt))
        val relock = policy.onInput(detected(), reacquireAt + FocusPolicy.MIN_STABLE_MILLIS)
        assertTrue(relock is FocusCommand.Acquire && relock.lock)
    }

    @Test
    fun `searches at frame center before the board has ever been seen`() {
        val policy = FocusPolicy()
        val command = policy.onInput(missed(lostLong = false), 0L)
        assertTrue(command is FocusCommand.Acquire && !command.lock)
        assertEquals(Point2Px(FRAME_WIDTH / 2f, FRAME_HEIGHT / 2f), (command as FocusCommand.Acquire).point)
    }

    @Test
    fun `spaces out search passes`() {
        val policy = FocusPolicy()
        assertTrue(policy.onInput(missed(lostLong = false), 0L) is FocusCommand.Acquire)
        assertNull(policy.onInput(missed(lostLong = false), FocusPolicy.SEARCH_RETRY_MILLIS - 1))
        assertTrue(
            policy.onInput(missed(lostLong = false), FocusPolicy.SEARCH_RETRY_MILLIS) is FocusCommand.Acquire,
        )
    }

    @Test
    fun `searches at the last known board position once the board has been seen`() {
        val policy = FocusPolicy()
        val lostAt = FocusPolicy.MIN_STABLE_MILLIS + 3_000L
        lockOn(policy)
        policy.onInput(missed(lostLong = true), lostAt)
        // markerCorners are already cleared by this point, so the policy has to remember them.
        val command = policy.onInput(missed(lostLong = true), lostAt + 33L)
        assertTrue(command is FocusCommand.Acquire && !command.lock)
        assertEquals(board, (command as FocusCommand.Acquire).point)
    }

    @Test
    fun `retries a lock the device failed to converge on, but not immediately`() {
        val policy = FocusPolicy()
        lockOn(policy)
        policy.onAcquireResult(success = false)

        val settled = FocusPolicy.MIN_STABLE_MILLIS
        assertNull(policy.onInput(detected(), settled + FocusPolicy.LOCK_RETRY_MIN_MILLIS - 1))
        val retry = policy.onInput(detected(), settled + FocusPolicy.LOCK_RETRY_MIN_MILLIS)
        assertTrue(retry is FocusCommand.Acquire && retry.lock)
    }

    @Test
    fun `stays locked when the device reports focus converged`() {
        val policy = FocusPolicy()
        lockOn(policy)
        policy.onAcquireResult(success = true)
        assertNull(policy.onInput(detected(), FocusPolicy.MIN_STABLE_MILLIS + 60_000L))
    }

    @Test
    fun `does nothing before any frame has arrived`() {
        val policy = FocusPolicy()
        val input = FocusInput(
            detected = false,
            lostLong = false,
            boardCenterPx = null,
            frameWidth = 0,
            frameHeight = 0,
        )
        assertNull(policy.onInput(input, 0L))
    }

    private companion object {
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 720
    }
}
