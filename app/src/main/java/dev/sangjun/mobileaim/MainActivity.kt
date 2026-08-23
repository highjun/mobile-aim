package dev.sangjun.mobileaim

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.sangjun.mobileaim.aim.AimViewModel
import dev.sangjun.mobileaim.app.ui.MainScreen
import dev.sangjun.mobileaim.app.ui.theme.MobileAimTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val aimViewModel: AimViewModel by viewModel()

    /**
     * "집중 모드" — silences notifications and ringtones via Do-Not-Disturb while aiming. Backed by
     * Compose state so the header button reflects it; also read from lifecycle callbacks to
     * re-apply on resume and release on stop. It can only *silence* an incoming call (DND), not
     * stop the call screen itself — a plain app can't do that.
     */
    private var isFocusMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on the whole time we're foreground (no WAKE_LOCK permission needed).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        setContent {
            var hasCameraPermission by remember {
                mutableStateOf(hasCameraPermission())
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasCameraPermission = granted || hasCameraPermission()
                if (!hasCameraPermission) {
                    Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
                }
            }

            LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            MobileAimTheme {
                MainScreen(
                    aimViewModel = aimViewModel,
                    hasCameraPermission = hasCameraPermission,
                    isFocusMode = isFocusMode,
                    onToggleFocusMode = ::toggleFocusMode,
                    onCameraError = { error ->
                        Toast.makeText(
                            this,
                            error.message ?: "Failed to start camera preview.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // System may reset DND while we were away; re-assert it if focus mode is on.
        if (isFocusMode) setDndSilenced(true)
    }

    override fun onStop() {
        super.onStop()
        // Don't leave the user's phone silent once they leave the app — release DND but keep
        // isFocusMode so onResume re-applies it.
        if (isFocusMode) setDndSilenced(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Sticky immersive: the bars come back on focus regain (e.g. after a swipe), so re-hide them.
        if (hasFocus) applyImmersiveMode()
    }

    /** Hides the status/navigation bars; they only reappear transiently on a swipe (sticky immersive). */
    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun toggleFocusMode() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (!isFocusMode) {
            if (notificationManager?.isNotificationPolicyAccessGranted != true) {
                // One-time grant: send the user to the DND-access screen, don't flip yet.
                Toast.makeText(this, "집중 모드에는 '방해 금지 접근' 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return
            }
            setDndSilenced(true)
            isFocusMode = true
        } else {
            setDndSilenced(false)
            isFocusMode = false
        }
    }

    /** [silenced] true → total-silence DND (alerts + ringtone muted); false → back to normal. */
    private fun setDndSilenced(silenced: Boolean) {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        if (!notificationManager.isNotificationPolicyAccessGranted) return
        notificationManager.setInterruptionFilter(
            if (silenced) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            },
        )
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
