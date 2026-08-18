package dev.sangjun.mobileaim.strategy

/**
 * Optional capability for a strategy whose pose is a fixed reference rather than (re)computed from
 * the camera frame every time — e.g. [NoMarkerAimTrackingService]'s manual zero, the same role a
 * physical scope's zeroing knobs play: once zeroed, the reticle stays at a fixed screen position
 * regardless of how the device rotates, exactly like a scope rigidly mounted to the barrel.
 */
@Deprecated("v1은 모드가 하나(수동 드래그)뿐이라 전략 전환 추상화가 불필요해짐")
interface ZeroableAimTracking {
    fun setZero()
}
