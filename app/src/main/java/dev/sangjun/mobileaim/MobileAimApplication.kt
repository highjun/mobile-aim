package dev.sangjun.mobileaim

import android.app.Application
import android.util.Log
import dev.sangjun.mobileaim.app.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.opencv.android.OpenCVLoader

class MobileAimApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Must happen before any org.opencv.* class is touched (e.g. by Koin constructing
        // ArucoMarkerDetectionService) — the AAR does not auto-load its native library, and
        // any OpenCV call before this throws UnsatisfiedLinkError.
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV native library failed to load")
        }

        // Koin's GlobalContext is a JVM static, so a second onCreate() in the same process
        // (Robolectric gives every test its own Application instance but reuses the JVM across
        // test classes) would otherwise throw KoinAppAlreadyStartedException.
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MobileAimApplication)
                modules(appModule)
            }
        }
    }

    private companion object {
        const val TAG = "MobileAimApplication"
    }
}
