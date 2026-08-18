package dev.sangjun.mobileaim.app.camera

import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Carries out a [FocusCommand] on a bound camera. Deliberately dumb — every "should we?" question
 * belongs to [FocusPolicy]; this only knows how to say it in CameraX terms.
 *
 * @param analysisUseCase the same `ImageAnalysis` the marker detector reads from. It is passed to
 *   [SurfaceOrientedMeteringPointFactory] so metering coordinates are interpreted against *that*
 *   surface — the points being aimed at come out of its frames, and the two-argument factory would
 *   silently reference `Preview` instead, whose aspect ratio need not match.
 */
@Deprecated("마커 검출 중심 오토포커스용이었음 — v1은 ImageAnalysis 자체가 없어 미사용")
internal class CameraFocusController(
    private val cameraControl: CameraControl,
    private val analysisUseCase: ImageAnalysis,
    private val callbackExecutor: Executor,
) {
    fun execute(command: FocusCommand, frameWidth: Int, frameHeight: Int, onResult: (Boolean) -> Unit) {
        when (command) {
            is FocusCommand.Release -> {
                // Per CameraControl's contract this also restores continuous AF, which is exactly
                // the state we want to fall back to once the board is gone.
                runCatching { cameraControl.cancelFocusAndMetering() }
                    .onFailure { Log.w(TAG, "cancelFocusAndMetering failed", it) }
            }

            is FocusCommand.Acquire -> acquire(command, frameWidth, frameHeight, onResult)
        }
    }

    private fun acquire(
        command: FocusCommand.Acquire,
        frameWidth: Int,
        frameHeight: Int,
        onResult: (Boolean) -> Unit,
    ) {
        if (frameWidth <= 0 || frameHeight <= 0) return
        val point = SurfaceOrientedMeteringPointFactory(
            frameWidth.toFloat(),
            frameHeight.toFloat(),
            analysisUseCase,
        ).createPoint(command.point.x, command.point.y)

        // AF only: metering AE/AWB on a black-and-white board would swing exposure around every
        // time focus is re-aimed, which has nothing to do with the problem being solved.
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .apply {
                if (command.lock) {
                    disableAutoCancel()
                } else {
                    setAutoCancelDuration(SEARCH_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
                }
            }
            .build()

        val future = try {
            cameraControl.startFocusAndMetering(action)
        } catch (error: IllegalArgumentException) {
            // Thrown when the device supports none of the metering points.
            Log.w(TAG, "startFocusAndMetering rejected the metering point", error)
            onResult(false)
            return
        }

        future.addListener(
            {
                val successful = try {
                    future.get().isFocusSuccessful
                } catch (error: Exception) {
                    // Also lands here when a newer request supersedes this one, which is benign.
                    Log.d(TAG, "Focus request did not complete: ${error.message}")
                    false
                }
                Log.i(TAG, "Focus ${if (command.lock) "lock" else "search"} at ${command.point}: successful=$successful")
                onResult(successful)
            },
            callbackExecutor,
        )
    }

    private companion object {
        const val TAG = "MobileAimFocus"

        /** Short enough that a failed search hands the lens back to continuous AF promptly. */
        const val SEARCH_AUTO_CANCEL_SECONDS = 3L
    }
}
