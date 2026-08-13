package dev.sangjun.mobileaim.strategy

/**
 * Optional capability for a strategy whose pose is a fixed reference rather than (re)computed from
 * the camera frame every time — e.g. [NoMarkerAimTrackingService]'s manual zero, the same role a
 * physical scope's zeroing knobs play: once zeroed, the reticle stays at a fixed screen position
 * regardless of how the device rotates, exactly like a scope rigidly mounted to the barrel.
 */
interface ZeroableAimTracking {
    fun setZero()
}
