package com.patrollink.presentation.screen

import android.view.TextureView
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextureMediaPlayerViewTest {
    @Test
    fun textureViewFillsPreviewContainer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = TextureMediaPlayerView(context)

        assertEquals(1, view.childCount)
        val textureView = view.getChildAt(0)
        assertTrue(textureView is TextureView)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, textureView.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, textureView.layoutParams.height)
    }
}
