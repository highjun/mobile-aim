package dev.sangjun.mobileaim.app

import dev.sangjun.mobileaim.aim.AimViewModel
import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.app.settings.SharedPrefsSettingsRepository
import dev.sangjun.mobileaim.logging.CaptureLogWriter
import dev.sangjun.mobileaim.logging.ICaptureLogWriter
import dev.sangjun.mobileaim.logging.ISensorMotionLogger
import dev.sangjun.mobileaim.logging.SensorMotionLogger
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<ISettingsRepository> { SharedPrefsSettingsRepository(androidContext()) }
    single<ISensorMotionLogger> { SensorMotionLogger(androidContext()) }
    single<ICaptureLogWriter> { CaptureLogWriter(androidContext()) }
    viewModel {
        AimViewModel(
            settingsRepository = get(),
            motionLogger = get(),
            captureLogWriter = get(),
        )
    }
}
