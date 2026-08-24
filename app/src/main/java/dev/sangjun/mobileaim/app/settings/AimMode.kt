package dev.sangjun.mobileaim.app.settings

/**
 * Which equipment the reticle is currently zeroed for. Real firearms travel fast enough that
 * ballistic drop is negligible at short/mid range, so a laser bore-sighter is a trustworthy zero
 * reference; a slow manual/spring gun drops noticeably even at a few meters, so its zero should
 * come from an actual fired shot, not the laser dot. Each mode keeps its own independent reticle
 * position (see [MarkerSettings]) rather than sharing one — switching equipment shouldn't require
 * re-zeroing every time.
 */
enum class AimMode { FIREARM, MANUAL_GUN }
