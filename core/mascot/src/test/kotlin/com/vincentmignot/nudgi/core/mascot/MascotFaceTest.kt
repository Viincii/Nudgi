package com.vincentmignot.nudgi.core.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotFaceTest {
    @Test
    fun `happy stands tall with its antenna up and eyes tipped apart`() {
        val face = MascotMood.Happy.face

        assertTrue(face.bodyStretch > 1f)
        assertEquals(0f, face.antennaDroop, 0f)
        assertTrue(face.eyeTilt < 0f)
    }

    @Test
    fun `worried slumps with its antenna down and eyes tipped together`() {
        val face = MascotMood.Worried.face

        assertTrue(face.bodyStretch < 1f)
        assertEquals(1f, face.antennaDroop, 0f)
        assertTrue(face.eyeTilt > 0f)
        assertTrue(face.bodyLean != 0f)
    }

    @Test
    fun `neutral rests at the reference pose`() {
        val face = MascotMood.Neutral.face

        assertEquals(1f, face.bodyStretch, 0f)
        assertEquals(0f, face.eyeTilt, 0f)
        assertEquals(0f, face.bodyLean, 0f)
    }

    @Test
    fun `antenna droop grows from happy to neutral to worried`() {
        val happy = MascotMood.Happy.face.antennaDroop
        val neutral = MascotMood.Neutral.face.antennaDroop
        val worried = MascotMood.Worried.face.antennaDroop

        assertTrue(happy < neutral && neutral < worried)
    }

    @Test
    fun `every mood stays within the drawable ranges`() {
        MascotMood.entries.forEach { mood ->
            val face = mood.face

            assertTrue("$mood eyeWidth", face.eyeWidth > 0f)
            assertTrue("$mood eyeHeight", face.eyeHeight > 0f)
            assertTrue("$mood antennaDroop", face.antennaDroop in 0f..1f)
            assertTrue("$mood bodyStretch", face.bodyStretch > 0f)
        }
    }
}
