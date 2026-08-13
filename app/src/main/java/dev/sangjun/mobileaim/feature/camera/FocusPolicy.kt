package dev.sangjun.mobileaim.feature.camera

import dev.sangjun.mobileaim.feature.geometry.Point2Px

/**
 * What the focus policy gets to see each time the aim state changes. All coordinates are in the
 * same analysis-frame pixel space the marker detector works in, so a point taken from here can be
 * handed straight to a metering-point factory built against the `ImageAnalysis` use case.
 *
 * @param detected a real detection happened on this frame.
 * @param lostLong the miss has gone on long enough that the pose itself was given up on (see
 *   [dev.sangjun.mobileaim.feature.aim.PoseMemoryPolicy]) — i.e. an already-debounced "really lost",
 *   not a single dropped frame.
 * @param boardCenterPx centre of the last known board corners, or null if none are on screen.
 */
data class FocusInput(
    val detected: Boolean,
    val lostLong: Boolean,
    val boardCenterPx: Point2Px?,
    val frameWidth: Int,
    val frameHeight: Int,
)

internal sealed interface FocusCommand {
    /**
     * Run an autofocus pass metered on [point]. [lock] holds the resulting lens position
     * indefinitely; otherwise the camera is expected to fall back to continuous AF on its own.
     */
    data class Acquire(val point: Point2Px, val lock: Boolean) : FocusCommand

    /** Drop any held focus and hand the lens back to continuous AF. */
    data object Release : FocusCommand
}

/**
 * Decides when to touch autofocus at all. Pure state — no Android APIs — so the whole decision
 * table is unit-testable; [CameraFocusController] does the actual CameraX calls.
 *
 * The symptom being fixed is focus *hunting*: with CameraX's default continuous AF the lens keeps
 * re-scanning, and the board is small and never in the same place, so the scan rarely settles on it.
 * The obvious-looking fix — re-metering on the marker every frame — makes hunting worse, because
 * every focus request restarts a scan. So the rule here is the opposite: once a detection has held
 * steady for [MIN_STABLE_MILLIS], focus once on where the board actually is and *hold* it, then
 * emit nothing at all for as long as detection keeps succeeding. That silence is the fix.
 *
 * Holding focus is only safe because it is released the moment [FocusInput.lostLong] says the board
 * is genuinely gone. An earlier attempt locked focus blindly at startup, before anything had been
 * detected, and could strand the lens on the wrong plane forever; here nothing is ever locked until
 * a real detection has proven both where the board is and that the current focus can resolve it.
 */
internal class FocusPolicy {
    private var locked = false
    private var detectedSinceMillis: Long? = null
    private var lastAcquireMillis: Long? = null
    private var lastKnownBoardCenter: Point2Px? = null

    fun onInput(input: FocusInput, nowMillis: Long): FocusCommand? {
        input.boardCenterPx?.let { lastKnownBoardCenter = it }

        if (input.lostLong && locked) {
            locked = false
            detectedSinceMillis = null
            // Clear the cooldown too: the board just went missing, so the search pass below should
            // start straight away rather than waiting out a gap measured from the lock.
            lastAcquireMillis = null
            return FocusCommand.Release
        }

        if (input.detected) {
            val since = detectedSinceMillis ?: nowMillis.also { detectedSinceMillis = it }
            if (locked) return null
            if (nowMillis - since < MIN_STABLE_MILLIS) return null
            if (!cooldownElapsed(nowMillis, LOCK_RETRY_MIN_MILLIS)) return null
            val target = input.boardCenterPx ?: lastKnownBoardCenter ?: return null
            locked = true
            lastAcquireMillis = nowMillis
            return FocusCommand.Acquire(target, lock = true)
        }

        detectedSinceMillis = null
        // Nothing has been locked yet, so nothing is being protected — periodically nudge AF at
        // wherever the board was last seen (centre of frame if it never has been) to help it find
        // the board in the first place. Auto-cancel is on for these, so they can't strand the lens.
        if (locked) return null
        if (!cooldownElapsed(nowMillis, SEARCH_RETRY_MILLIS)) return null
        val target = lastKnownBoardCenter ?: centerOf(input) ?: return null
        lastAcquireMillis = nowMillis
        return FocusCommand.Acquire(target, lock = false)
    }

    /** Report of how the last [FocusCommand.Acquire] turned out, so a failed lock gets retried. */
    fun onAcquireResult(success: Boolean) {
        if (!success) locked = false
    }

    private fun cooldownElapsed(nowMillis: Long, cooldownMillis: Long): Boolean {
        val last = lastAcquireMillis ?: return true
        return nowMillis - last >= cooldownMillis
    }

    private fun centerOf(input: FocusInput): Point2Px? {
        if (input.frameWidth <= 0 || input.frameHeight <= 0) return null
        return Point2Px(input.frameWidth / 2f, input.frameHeight / 2f)
    }

    companion object {
        /**
         * How long detection has to hold before focus is locked to it. Time-based rather than a
         * frame count because the state this reads from is conflated — a count would mean different
         * things at different frame rates.
         */
        const val MIN_STABLE_MILLIS = 400L

        /** Floor on re-locking, so a lock the device keeps failing to converge on can't thrash. */
        const val LOCK_RETRY_MIN_MILLIS = 1_500L

        /** Gap between search passes while nothing is locked. Long enough to let each pass settle. */
        const val SEARCH_RETRY_MILLIS = 4_000L
    }
}
