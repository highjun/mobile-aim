package dev.sangjun.mobileaim

import android.app.Application
import android.util.Log
import dev.sangjun.mobileaim.di.appModule
import org.koin.android.ext.koin.androidContext
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

        startKoin {
            androidContext(this@MobileAimApplication)
            modules(appModule)
        }
    }

    private companion object {
        const val TAG = "MobileAimApplication"
    }
}
