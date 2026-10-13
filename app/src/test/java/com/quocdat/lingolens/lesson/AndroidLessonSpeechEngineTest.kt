package com.quocdat.lingolens.lesson

import android.speech.tts.Voice
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AndroidLessonSpeechEngineTest {
    @Test fun `adapter reads actual voice locale network requirement and quality`() {
        val native = Voice("actual-us", Locale.US, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
        val voice = AndroidLessonSpeechEngine.describe(native)
        assertEquals("en-US", voice.languageTag)
        assertFalse(voice.requiresNetwork)
        assertEquals(Voice.QUALITY_HIGH, voice.quality)
        val network = Voice("actual-network", Locale.UK, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, true, emptySet())
        val other = AndroidLessonSpeechEngine.describe(network)
        assertEquals("en-GB", other.languageTag)
        assertTrue(other.requiresNetwork)
    }
}
