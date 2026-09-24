package com.vincentmignot.nudgi.core.mascot

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SIZE_PX = 200

// A library manifest has no targetSdk, so Robolectric would fall back to an SDK without native graphics.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MascotBitmapTest {
    @Test
    fun `draws the body on a transparent square`() {
        val bitmap = renderMascot(MascotMood.Happy, SIZE_PX)

        assertEquals(SIZE_PX, bitmap.width)
        assertEquals(SIZE_PX, bitmap.height)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        // Between the eyes, well inside the blob.
        assertEquals(0xFF7BD8C4.toInt(), bitmap.getPixel(SIZE_PX / 2, (SIZE_PX * 0.75f).toInt()))
    }

    @Test
    fun `each mood renders its own face`() {
        val happy = renderMascot(MascotMood.Happy, SIZE_PX)

        assertFalse(happy.sameAs(renderMascot(MascotMood.Worried, SIZE_PX)))
    }
}
