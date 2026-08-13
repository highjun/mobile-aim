package dev.sangjun.mobileaim.feature.marker

import org.opencv.core.Mat

interface IMarkerDetectionService {
    /** Analyzes a single already-converted grayscale frame; does not release [gray]. */
    fun analyze(gray: Mat, rotationDegrees: Int, timestampMillis: Long): MarkerDetectionResult
}
