package dev.sangjun.mobileaim.aim

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMemoryPolicyTest {

    @Test
    fun `keeps the pose just under the forget threshold`() {
        assertFalse(PoseMemoryPolicy.shouldForgetPose(PoseMemoryPolicy.FORGET_AFTER_MILLIS - 1))
    }

    @Test
    fun `forgets the pose at exactly the threshold`() {
        assertTrue(PoseMemoryPolicy.shouldForgetPose(PoseMemoryPolicy.FORGET_AFTER_MILLIS))
    }

    @Test
    fun `forgets the pose well past the threshold`() {
        assertTrue(PoseMemoryPolicy.shouldForgetPose(PoseMemoryPolicy.FORGET_AFTER_MILLIS * 10))
    }

    @Test
    fun `keeps the pose for a brief miss`() {
        assertFalse(PoseMemoryPolicy.shouldForgetPose(0L))
        assertFalse(PoseMemoryPolicy.shouldForgetPose(100L))
    }
}
