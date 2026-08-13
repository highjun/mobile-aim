package dev.sangjun.mobileaim.logging

/**
 * One "조준 완료" capture event: the photo file shares [captureId] as its filename stem, so the two
 * can be matched up after the fact. Sensor/pose fields are nullable — a capture is still worth
 * recording even if a reading isn't available yet (e.g. tracking lost right as the shutter is hit).
 */
data class CaptureRow(
    val captureId: String,
    val timestampMillis: Long,
    val strategy: String,
    val isMarkerDetected: Boolean,
    val rangeMeters: Float,
    val xOffsetMm: Double,
    val yOffsetMm: Double,
    val accel: DoubleArray?,
    val gyro: DoubleArray?,
) {
    fun toCsvLine(): String = listOf(
        captureId,
        timestampMillis,
        strategy,
        isMarkerDetected,
        rangeMeters,
        xOffsetMm,
        yOffsetMm,
        accel?.getOrNull(0),
        accel?.getOrNull(1),
        accel?.getOrNull(2),
        gyro?.getOrNull(0),
        gyro?.getOrNull(1),
        gyro?.getOrNull(2),
    ).joinToString(",") { it?.toString() ?: "" }

    companion object {
        const val CSV_HEADER =
            "capture_id,timestamp_ms,strategy,marker_detected,range_m,x_offset_mm,y_offset_mm," +
                "accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z"
    }
}
