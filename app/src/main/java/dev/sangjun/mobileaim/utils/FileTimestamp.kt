package dev.sangjun.mobileaim.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FileTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")

/**
 * A human-readable, still-sortable timestamp (local device time) for naming session files — e.g.
 * `2026-08-24-10-15-30-482`. Millisecond precision isn't for display so much as collision safety:
 * without it, two captures/session-starts inside the same second would produce the same name.
 */
fun fileTimestampNow(): String = LocalDateTime.now().format(FileTimestampFormatter)
