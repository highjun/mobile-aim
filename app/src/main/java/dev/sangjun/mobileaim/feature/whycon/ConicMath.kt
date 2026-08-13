package dev.sangjun.mobileaim.feature.whycon

import kotlin.math.cos
import kotlin.math.sin

/** A fitted ellipse in pixel space: centre, semi-axes (a = major, b = minor), and the major axis's angle from +x. */
data class RotatedEllipse(val cx: Double, val cy: Double, val a: Double, val b: Double, val angleRad: Double)

/**
 * Converts a fitted ellipse into its conic matrix `C` (3x3 symmetric) such that a homogeneous
 * pixel point `p = (x, y, 1)` lies on the ellipse boundary iff `pᵀCp = 0`. Pure coordinate-transform
 * algebra, independent of any specific fiducial-marker implementation — this is the standard
 * "ellipse as a quadratic form" fact, not a fiducial-detection-specific technique.
 *
 * Derivation: in the ellipse's own local frame (origin at centre, local x-axis along the major
 * axis), the boundary is `x²/a² + y²/b² = 1`, i.e. `pᵀDp = 0` for `D = diag(1/a², 1/b², -1)`. A
 * pixel point maps to its local coordinates via `p_local = M · p_pixel`, where `M` undoes the
 * ellipse's rotation and translation. Substituting `p_local` into `p_localᵀ D p_local = 0` gives
 * `p_pixelᵀ (MᵀDM) p_pixel = 0`, so `C = MᵀDM`.
 */
object ConicMath {
    fun ellipseToConic(ellipse: RotatedEllipse): Array<DoubleArray> {
        val cosT = cos(ellipse.angleRad)
        val sinT = sin(ellipse.angleRad)
        // Maps a pixel-space homogeneous point to the ellipse's local (unrotated, centred) frame.
        val m = arrayOf(
            doubleArrayOf(cosT, sinT, -ellipse.cx * cosT - ellipse.cy * sinT),
            doubleArrayOf(-sinT, cosT, ellipse.cx * sinT - ellipse.cy * cosT),
            doubleArrayOf(0.0, 0.0, 1.0),
        )
        val d = arrayOf(
            doubleArrayOf(1.0 / (ellipse.a * ellipse.a), 0.0, 0.0),
            doubleArrayOf(0.0, 1.0 / (ellipse.b * ellipse.b), 0.0),
            doubleArrayOf(0.0, 0.0, -1.0),
        )
        return multiply(multiply(transpose(m), d), m)
    }

    /** `pᵀCp` for homogeneous `p = (x, y, 1)` — zero exactly on the ellipse boundary [conic] describes. */
    fun evaluate(conic: Array<DoubleArray>, x: Double, y: Double): Double {
        val p = doubleArrayOf(x, y, 1.0)
        var result = 0.0
        for (i in 0..2) {
            var rowDotP = 0.0
            for (j in 0..2) rowDotP += conic[i][j] * p[j]
            result += p[i] * rowDotP
        }
        return result
    }

    private fun transpose(m: Array<DoubleArray>): Array<DoubleArray> = Array(3) { i -> DoubleArray(3) { j -> m[j][i] } }

    private fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(3) { i -> DoubleArray(3) { j -> (0..2).sumOf { k -> a[i][k] * b[k][j] } } }
}
