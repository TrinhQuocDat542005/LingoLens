package com.quocdat.lingolens.lesson

import org.junit.Assert.*
import org.junit.Test

class LessonSpeechControllerTest {
    private val offlineUs = LessonVoice("us-offline", "en-US", false, 300)
    private val networkUs = LessonVoice("us-network", "en-US", true, 500)

    private class FakeEngine : LessonSpeechEngine {
        override val defaultEnginePackage = "test.tts.engine"
        var voices = emptyList<LessonVoice>()
        var initialized: ((Boolean) -> Unit)? = null
        var events: ((LessonSpeechEvent) -> Unit)? = null
        var acceptsSpeech = true
        var stopCount = 0
        var closeCount = 0
        val spoken = mutableListOf<Triple<String, LessonVoice, String>>()
        override fun initialize(onInitialized: (Boolean) -> Unit, onUtterance: (LessonSpeechEvent) -> Unit) {
            initialized = onInitialized
            events = onUtterance
        }
        override fun availableVoices() = voices
        override fun speak(text: String, voice: LessonVoice, utteranceId: String): Boolean {
            spoken += Triple(text, voice, utteranceId)
            return acceptsSpeech
        }
        override fun stop() { stopCount++ }
        override fun close() { closeCount++ }
    }

    private fun ready(engine: FakeEngine, online: Boolean = true): LessonSpeechController {
        val controller = LessonSpeechController(engine, online)
        controller.start()
        engine.initialized!!.invoke(true)
        return controller
    }

    @Test fun `missing voice offers explicit fallback and engine package`() {
        val controller = ready(FakeEngine())
        assertEquals(LessonSpeechStatus.MISSING_VOICE, controller.state.value.status)
        assertEquals("test.tts.engine", controller.defaultEnginePackage)
        assertFalse(controller.speak("apple"))
    }

    @Test fun `UK voice cannot satisfy en US contract`() {
        val engine = FakeEngine().apply { voices = listOf(LessonVoice("uk", "en-GB", false, 500)) }
        val controller = ready(engine)
        assertEquals(LessonSpeechStatus.MISSING_VOICE, controller.state.value.status)
        assertNull(controller.state.value.selectedVoice)
        assertFalse(controller.speak("apple"))
        assertTrue(engine.spoken.isEmpty())
    }

    @Test fun `offline US voice wins over higher quality network voice`() {
        val engine = FakeEngine().apply { voices = listOf(networkUs, offlineUs) }
        val controller = ready(engine)
        assertEquals(offlineUs, controller.state.value.selectedVoice)
        assertTrue(controller.speak("apple"))
        assertEquals(offlineUs, engine.spoken.single().second)
    }

    @Test fun `network only US voice requires a connection and never silently speaks offline`() {
        val engine = FakeEngine().apply { voices = listOf(networkUs) }
        val controller = ready(engine, online = false)
        assertEquals(LessonSpeechStatus.NETWORK_REQUIRED, controller.state.value.status)
        assertFalse(controller.speak("apple"))
        assertTrue(engine.spoken.isEmpty())
        controller.refreshVoices(online = true)
        assertEquals(LessonSpeechStatus.READY, controller.state.value.status)
        assertTrue(controller.state.value.selectedVoice!!.requiresNetwork)
        assertTrue(controller.speak("apple"))
        controller.refreshVoices(online = false)
        assertFalse(controller.state.value.speaking)
        assertEquals(LessonSpeechStatus.NETWORK_REQUIRED, controller.state.value.status)
    }

    @Test fun `refresh after voice installation selects new offline US voice`() {
        val engine = FakeEngine()
        val controller = ready(engine, online = false)
        engine.voices = listOf(offlineUs)
        controller.refreshVoices(online = false)
        assertEquals(LessonSpeechStatus.READY, controller.state.value.status)
        assertEquals(offlineUs, controller.state.value.selectedVoice)
        assertTrue(controller.speak("apple"))
    }

    @Test fun `initialization failure cannot report ready or speak`() {
        val engine = FakeEngine().apply { voices = listOf(offlineUs) }
        val controller = LessonSpeechController(engine)
        controller.start()
        engine.initialized!!.invoke(false)
        assertEquals(LessonSpeechStatus.ERROR, controller.state.value.status)
        assertFalse(controller.speak("apple"))
        controller.refreshVoices(online = true)
        assertEquals(LessonSpeechStatus.ERROR, controller.state.value.status)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test fun `speak failure exposes error and refresh allows explicit retry`() {
        val engine = FakeEngine().apply { voices = listOf(offlineUs); acceptsSpeech = false }
        val controller = ready(engine)
        assertFalse(controller.speak("apple"))
        assertEquals(LessonSpeechStatus.ERROR, controller.state.value.status)
        assertFalse(controller.state.value.speaking)
        assertNotNull(controller.state.value.error)
        engine.acceptsSpeech = true
        controller.refreshVoices(online = true)
        assertTrue(controller.speak("apple"))
    }

    @Test fun `new speech stops old utterance and ignores its late callbacks`() {
        val engine = FakeEngine().apply { voices = listOf(offlineUs) }
        val controller = ready(engine)
        controller.speak("apple")
        val oldId = engine.spoken.last().third
        val previousStops = engine.stopCount
        controller.speak("book")
        val currentId = engine.spoken.last().third
        assertNotEquals(oldId, currentId)
        assertTrue(engine.stopCount > previousStops)
        engine.events!!.invoke(LessonSpeechEvent.Failed(oldId))
        engine.events!!.invoke(LessonSpeechEvent.Completed(oldId))
        assertEquals(LessonSpeechStatus.READY, controller.state.value.status)
        assertTrue(controller.state.value.speaking)
        engine.events!!.invoke(LessonSpeechEvent.Completed(currentId))
        assertFalse(controller.state.value.speaking)
    }

    @Test fun `utterance error is visible and background stop fences later callbacks`() {
        val engine = FakeEngine().apply { voices = listOf(offlineUs) }
        val controller = ready(engine)
        controller.speak("apple")
        engine.events!!.invoke(LessonSpeechEvent.Failed(engine.spoken.last().third))
        assertEquals(LessonSpeechStatus.ERROR, controller.state.value.status)
        controller.refreshVoices(online = true)
        controller.speak("book")
        val stoppedId = engine.spoken.last().third
        controller.stop()
        engine.events!!.invoke(LessonSpeechEvent.Started(stoppedId))
        engine.events!!.invoke(LessonSpeechEvent.Failed(stoppedId))
        assertEquals(LessonSpeechStatus.READY, controller.state.value.status)
        assertFalse(controller.state.value.speaking)
    }

    @Test fun `close is idempotent and ignores initialization and utterance callbacks`() {
        val engine = FakeEngine().apply { voices = listOf(offlineUs) }
        val controller = LessonSpeechController(engine)
        controller.start()
        controller.close()
        engine.initialized!!.invoke(true)
        engine.events!!.invoke(LessonSpeechEvent.Started("lesson-1"))
        controller.close()
        controller.refreshVoices(online = true)
        assertFalse(controller.speak("apple"))
        assertEquals(LessonSpeechStatus.CLOSED, controller.state.value.status)
        assertEquals(1, engine.closeCount)
        assertTrue(engine.spoken.isEmpty())
    }
}
