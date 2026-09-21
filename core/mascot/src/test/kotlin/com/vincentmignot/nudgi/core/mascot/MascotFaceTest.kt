package com.vincentmignot.nudgi.core.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotFaceTest {
    @Test
    fun `happy smiles and keeps its antenna up`() {
        val face = MascotMood.Happy.toFace()

        assertTrue(face.mouthCurve > 0f)
        assertEquals(0f, face.browTilt, 0f)
        assertEquals(0f, face.antennaDroop, 0f)
    }

    @Test
    fun `worried frowns with raised eyebrows and a drooping antenna`() {
        val face = MascotMood.Worried.toFace()

        assertTrue(face.mouthCurve < 0f)
        assertEquals(1f, face.browTilt, 0f)
        assertEquals(1f, face.antennaDroop, 0f)
    }

    @Test
    fun `neutral sits between happy and worried`() {
        val happy = MascotMood.Happy.toFace()
        val neutral = MascotMood.Neutral.toFace()
        val worried = MascotMood.Worried.toFace()

        assertTrue(neutral.mouthCurve < happy.mouthCurve && neutral.mouthCurve > worried.mouthCurve)
        assertTrue(neutral.antennaDroop > happy.antennaDroop && neutral.antennaDroop < worried.antennaDroop)
    }

    @Test
    fun `every mood stays within the drawable ranges`() {
        MascotMood.entries.forEach { mood ->
            val face = mood.toFace()

            assertTrue("$mood mouthCurve", face.mouthCurve in -1f..1f)
            assertTrue("$mood browTilt", face.browTilt in 0f..1f)
            assertTrue("$mood antennaDroop", face.antennaDroop in 0f..1f)
            assertTrue("$mood cheekAlpha", face.cheekAlpha in 0f..1f)
            assertTrue("$mood eyeScale", face.eyeScale > 0f)
        }
    }
}
