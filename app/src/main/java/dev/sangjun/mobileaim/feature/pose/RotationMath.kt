package dev.sangjun.mobileaim.feature.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rodrigues-vector <-> quaternion conversions, in pure Kotlin so the (singularity-prone) math can
 * be unit tested on the JVM without OpenCV.
 *
 * Rodrigues vectors cannot be blended component-wise: a rotation of pi about axis n is the *same*
 * rotation as pi about -n, so two frames of an essentially motionless marker can report nearly
 * opposite vectors. Averaging those drives the result toward zero, i.e. the identity rotation.
 * Quaternions have their own two-fold ambiguity (q and -q), but it is trivially handled by picking
 * the hemisphere nearest the previous sample — see [alignHemisphere].
 */
internal object RotationMath {

    /** Quaternion laid out as [w, x, y, z]. */
    fun rvecToQuaternion(rvec: DoubleArray): DoubleArray {
        val angle = sqrt(rvec[0] * rvec[0] + rvec[1] * rvec[1] + rvec[2] * rvec[2])
        if (angle < EPSILON) return doubleArrayOf(1.0, 0.0, 0.0, 0.0)
        val half = angle / 2.0
        val s = sin(half) / angle
        return doubleArrayOf(cos(half), rvec[0] * s, rvec[1] * s, rvec[2] * s)
    }

    fun quaternionToRvec(quaternion: DoubleArray): DoubleArray {
        val q = normalize(quaternion)
        // Canonicalise to w >= 0 so the recovered angle lands in [0, pi].
        val w = if (q[0] < 0.0) -q[0] else q[0]
        val sign = if (q[0] < 0.0) -1.0 else 1.0
        val x = q[1] * sign
        val y = q[2] * sign
        val z = q[3] * sign

        val sinHalf = sqrt((1.0 - w * w).coerceAtLeast(0.0))
        if (sinHalf < EPSILON) return doubleArrayOf(0.0, 0.0, 0.0)
        val angle = 2.0 * acos(w.coerceIn(-1.0, 1.0))
        val scale = angle / sinHalf
        return doubleArrayOf(x * scale, y * scale, z * scale)
    }

    fun normalize(quaternion: DoubleArray): DoubleArray {
        val norm = sqrt(
            quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1] +
                quaternion[2] * quaternion[2] + quaternion[3] * quaternion[3],
        )
        if (norm < EPSILON) return doubleArrayOf(1.0, 0.0, 0.0, 0.0)
        return DoubleArray(4) { quaternion[it] / norm }
    }

    fun dot(a: DoubleArray, b: DoubleArray): Double =
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

    /** Returns [candidate], negated if needed, so it lies in the same hemisphere as [reference]. */
    fun alignHemisphere(reference: DoubleArray, candidate: DoubleArray): DoubleArray =
        if (dot(reference, candidate) < 0.0) DoubleArray(4) { -candidate[it] } else candidate

    /** Normalised linear interpolation; adequate for the small per-frame deltas we damp. */
    fun nlerp(from: DoubleArray, to: DoubleArray, t: Double): DoubleArray {
        val aligned = alignHemisphere(from, to)
        return normalize(DoubleArray(4) { from[it] + t * (aligned[it] - from[it]) })
    }

    /** Angle between the rotations represented by two Rodrigues vectors, in degrees. */
    fun angleBetweenDegrees(rvecA: DoubleArray, rvecB: DoubleArray): Double {
        val a = normalize(rvecToQuaternion(rvecA))
        val b = normalize(rvecToQuaternion(rvecB))
        val d = abs(dot(a, b)).coerceIn(0.0, 1.0)
        return Math.toDegrees(2.0 * acos(d))
    }

    /** The rotation angle a Rodrigues vector itself encodes, in degrees. */
    fun angleDegrees(rvec: DoubleArray): Double =
        Math.toDegrees(sqrt(rvec[0] * rvec[0] + rvec[1] * rvec[1] + rvec[2] * rvec[2]))

    /** Hamilton product of two quaternions, both [w, x, y, z]. */
    fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        val (aw, ax, ay, az) = a
        val (bw, bx, by, bz) = b
        return doubleArrayOf(
            aw * bw - ax * bx - ay * by - az * bz,
            aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
        )
    }

    /** Conjugate; equals the inverse for a unit quaternion. */
    fun conjugate(q: DoubleArray): DoubleArray = doubleArrayOf(q[0], -q[1], -q[2], -q[3])

    /** Rotates 3D vector [v] by unit quaternion [q]: v' = q * (0,v) * q^-1. */
    fun rotateVector(q: DoubleArray, v: DoubleArray): DoubleArray {
        val vq = doubleArrayOf(0.0, v[0], v[1], v[2])
        val result = multiply(multiply(q, vq), conjugate(q))
        return doubleArrayOf(result[1], result[2], result[3])
    }

    /** Rotates 3D vector [v] by the rotation a Rodrigues vector encodes. */
    fun rotateVectorByRvec(rvec: DoubleArray, v: DoubleArray): DoubleArray =
        rotateVector(normalize(rvecToQuaternion(rvec)), v)

    /**
     * Caps a rotation's angle at [maxDegrees], keeping its axis. A Rodrigues vector's magnitude
     * *is* the rotation angle in radians, so this is a direct rescale — but only once the vector is
     * known to be a genuine small-angle rotation; callers doing this relative to a reference
     * orientation should go through [relativeRvec] first (see [OpenCvPoseEstimationService]) so
     * "small" is measured from the physically expected pose, not from the identity.
     */
    fun clampAngleDegrees(rvec: DoubleArray, maxDegrees: Double): DoubleArray {
        val angleRad = sqrt(rvec[0] * rvec[0] + rvec[1] * rvec[1] + rvec[2] * rvec[2])
        val maxRad = Math.toRadians(maxDegrees)
        if (angleRad <= maxRad || angleRad < EPSILON) return rvec
        val scale = maxRad / angleRad
        return DoubleArray(3) { rvec[it] * scale }
    }

    /** The rotation [deltaRvec] such that composing it after [fromRvec] yields [toRvec]. */
    fun relativeRvec(fromRvec: DoubleArray, toRvec: DoubleArray): DoubleArray {
        val qFrom = normalize(rvecToQuaternion(fromRvec))
        val qTo = normalize(rvecToQuaternion(toRvec))
        return quaternionToRvec(multiply(conjugate(qFrom), qTo))
    }

    /** Composes [deltaRvec] after [baseRvec] (the inverse of [relativeRvec]). */
    fun composeRvec(baseRvec: DoubleArray, deltaRvec: DoubleArray): DoubleArray {
        val qBase = normalize(rvecToQuaternion(baseRvec))
        val qDelta = normalize(rvecToQuaternion(deltaRvec))
        return quaternionToRvec(multiply(qBase, qDelta))
    }

    /**
     * Caps [rvec]'s tilt *relative to* [referenceRvec] at [maxDegrees], preserving its tilt axis
     * relative to that reference. Used as a final safety net on a resolved pose — see
     * [dev.sangjun.mobileaim.feature.pose.OpenCvPoseEstimationService].
     */
    fun clampRelativeToReference(rvec: DoubleArray, referenceRvec: DoubleArray, maxDegrees: Double): DoubleArray {
        val relative = relativeRvec(referenceRvec, rvec)
        val clampedRelative = clampAngleDegrees(relative, maxDegrees)
        return composeRvec(referenceRvec, clampedRelative)
    }

    private const val EPSILON = 1e-12
}

private operator fun DoubleArray.component1() = this[0]
private operator fun DoubleArray.component2() = this[1]
private operator fun DoubleArray.component3() = this[2]
private operator fun DoubleArray.component4() = this[3]
