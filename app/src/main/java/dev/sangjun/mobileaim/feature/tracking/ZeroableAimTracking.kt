package dev.sangjun.mobileaim.feature.tracking

/**
 * Optional capability for a strategy whose pose is anchored to a user-set reference rather than
 * (re)computed from the camera frame every time — e.g. [NoMarkerAimTrackingService]'s manual zero,
 * the same role a physical scope's zeroing knobs play.
 */
interface ZeroableAimTracking {
    fun setZero(rotationDeviceFrame: DoubleArray, rotationDegrees: Int)
}
