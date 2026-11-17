package dev.sangjun.mobileaim.strategy

@Deprecated("v1은 모드가 하나(수동 드래그)뿐이라 전략 전환 추상화가 불필요해짐")
enum class AimStrategy { CHARUCO, WHYCON, NO_MARKER }
