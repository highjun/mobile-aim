package dev.sangjun.mobileaim.di

import dev.sangjun.mobileaim.feature.aim.AimViewModel
import dev.sangjun.mobileaim.feature.capture.CaptureLogWriter
import dev.sangjun.mobileaim.feature.capture.ICaptureLogWriter
import dev.sangjun.mobileaim.feature.drift.IDriftMonitorService
import dev.sangjun.mobileaim.feature.drift.OrbDriftMonitorService
import dev.sangjun.mobileaim.feature.marker.CharucoMarkerDetectionService
import dev.sangjun.mobileaim.feature.marker.IMarkerDetectionService
import dev.sangjun.mobileaim.feature.motion.IGravityProvider
import dev.sangjun.mobileaim.feature.motion.IRotationVectorProvider
import dev.sangjun.mobileaim.feature.motion.ISensorMotionLogger
import dev.sangjun.mobileaim.feature.motion.SensorGravityProvider
import dev.sangjun.mobileaim.feature.motion.SensorMotionLogger
import dev.sangjun.mobileaim.feature.motion.SensorRotationVectorProvider
import dev.sangjun.mobileaim.feature.pose.Camera2IntrinsicsProvider
import dev.sangjun.mobileaim.feature.pose.ICameraIntrinsicsProvider
import dev.sangjun.mobileaim.feature.pose.IPoseEstimationService
import dev.sangjun.mobileaim.feature.pose.OpenCvPoseEstimationService
import dev.sangjun.mobileaim.feature.settings.ISettingsRepository
import dev.sangjun.mobileaim.feature.settings.SharedPrefsSettingsRepository
import dev.sangjun.mobileaim.feature.tracking.AimStrategy
import dev.sangjun.mobileaim.feature.tracking.CharucoAimTrackingService
import dev.sangjun.mobileaim.feature.tracking.IAimTrackingService
import dev.sangjun.mobileaim.feature.tracking.NoMarkerAimTrackingService
import dev.sangjun.mobileaim.feature.tracking.StrategySwitchingAimTrackingService
import dev.sangjun.mobileaim.feature.whycon.WhyConAimTrackingService
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
