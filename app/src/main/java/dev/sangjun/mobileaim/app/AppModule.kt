package dev.sangjun.mobileaim.app

import dev.sangjun.mobileaim.aim.AimViewModel
import dev.sangjun.mobileaim.app.sensor.IGravityProvider
import dev.sangjun.mobileaim.app.sensor.IRotationVectorProvider
import dev.sangjun.mobileaim.app.sensor.SensorGravityProvider
import dev.sangjun.mobileaim.app.sensor.SensorRotationVectorProvider
import dev.sangjun.mobileaim.app.settings.ISettingsRepository
import dev.sangjun.mobileaim.app.settings.SharedPrefsSettingsRepository
import dev.sangjun.mobileaim.driftdetection.IDriftMonitorService
import dev.sangjun.mobileaim.driftdetection.OrbDriftMonitorService
import dev.sangjun.mobileaim.logging.CaptureLogWriter
import dev.sangjun.mobileaim.logging.ICaptureLogWriter
import dev.sangjun.mobileaim.logging.ISensorMotionLogger
import dev.sangjun.mobileaim.logging.SensorMotionLogger
import dev.sangjun.mobileaim.strategy.AimStrategy
import dev.sangjun.mobileaim.strategy.IAimTrackingService
import dev.sangjun.mobileaim.strategy.NoMarkerAimTrackingService
import dev.sangjun.mobileaim.strategy.StrategySwitchingAimTrackingService
import dev.sangjun.mobileaim.strategy.checkermarker.Camera2IntrinsicsProvider
import dev.sangjun.mobileaim.strategy.checkermarker.CharucoAimTrackingService
import dev.sangjun.mobileaim.strategy.checkermarker.CharucoMarkerDetectionService
import dev.sangjun.mobileaim.strategy.checkermarker.ICameraIntrinsicsProvider
import dev.sangjun.mobileaim.strategy.checkermarker.IMarkerDetectionService
import dev.sangjun.mobileaim.strategy.checkermarker.IPoseEstimationService
import dev.sangjun.mobileaim.strategy.checkermarker.OpenCvPoseEstimationService
import dev.sangjun.mobileaim.strategy.circlemarker.WhyConAimTrackingService
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single<ICameraIntrinsicsProvider> { Camera2IntrinsicsProvider(androidContext()) }
    single<IMarkerDetectionService> { CharucoMarkerDetectionService() }
    single<IPoseEstimationService> { OpenCvPoseEstimationService() }
    // Per-strategy services, keyed by AimStrategy.
    single<IAimTrackingService>(named(AimStrategy.CHARUCO)) { CharucoAimTrackingService(markerService = get(), poseService = get()) }
    single<IAimTrackingService>(named(AimStrategy.WHYCON)) { WhyConAimTrackingService() }
    single<IAimTrackingService>(named(AimStrategy.NO_MARKER)) { NoMarkerAimTrackingService() }
    single<IAimTrackingService> {
        StrategySwitchingAimTrackingService(
            strategies = mapOf(
                AimStrategy.CHARUCO to get(named(AimStrategy.CHARUCO)),
                AimStrategy.WHYCON to get(named(AimStrategy.WHYCON)),
                AimStrategy.NO_MARKER to get(named(AimStrategy.NO_MARKER)),
            ),
            settingsRepository = get(),
        )
    }
    single<ISettingsRepository> { SharedPrefsSettingsRepository(androidContext()) }
    single<IGravityProvider> { SensorGravityProvider(androidContext()) }
    single<IRotationVectorProvider> { SensorRotationVectorProvider(androidContext()) }
    single<ISensorMotionLogger> { SensorMotionLogger(androidContext()) }
    single<ICaptureLogWriter> { CaptureLogWriter(androidContext()) }
    single<IDriftMonitorService> { OrbDriftMonitorService() }
    viewModel {
        AimViewModel(
            trackingService = get(),
            intrinsicsProvider = get(),
            settingsRepository = get(),
            gravityProvider = get(),
            rotationVectorProvider = get(),
            motionLogger = get(),
            captureLogWriter = get(),
            driftMonitor = get(),
        )
    }
}
