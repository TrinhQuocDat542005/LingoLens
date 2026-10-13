package com.quocdat.lingolens.lesson

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice

/** Device TTS only. Call methods on the main thread; native callbacks are marshalled there. */
class AndroidLessonSpeechEngine(context: Context) : LessonSpeechEngine {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var closed = false
    override val defaultEnginePackage: String? get() = engine?.defaultEngine

    override fun initialize(onInitialized: (Boolean) -> Unit, onUtterance: (LessonSpeechEvent) -> Unit) {
        check(engine == null && !closed) { "Lesson speech engine has already been started or closed" }
        val created = TextToSpeech(context) { status ->
            // Always post: initialization may complete before TextToSpeech's constructor returns.
            dispatch {
                initialized = status == TextToSpeech.SUCCESS
                onInitialized(initialized)
            }
        }
        engine = created
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null) dispatch { onUtterance(LessonSpeechEvent.Started(utteranceId)) }
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) dispatch { onUtterance(LessonSpeechEvent.Completed(utteranceId)) }
            }
            @Deprecated("Required by the platform abstract listener")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null) dispatch { onUtterance(LessonSpeechEvent.Failed(utteranceId)) }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != null) dispatch { onUtterance(LessonSpeechEvent.Failed(utteranceId)) }
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId != null) dispatch { onUtterance(LessonSpeechEvent.Completed(utteranceId)) }
            }
        })
    }

    override fun availableVoices(): List<LessonVoice> =
        if (initialized && !closed) engine?.voices.orEmpty().map(::describe) else emptyList()

    override fun speak(text: String, voice: LessonVoice, utteranceId: String): Boolean {
        if (!initialized || closed) return false
        val current = engine ?: return false
        val native = current.voices.orEmpty().firstOrNull { describe(it) == voice } ?: return false
        if (native.locale.language != "en" || native.locale.country != "US") return false
        if (current.setVoice(native) != TextToSpeech.SUCCESS) return false
        return current.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
    }

    override fun stop() { if (!closed) engine?.stop() }

    override fun close() {
        if (closed) return
        closed = true
        initialized = false
        handler.removeCallbacksAndMessages(null)
        val current = engine
        engine = null
        try { current?.stop() } finally { current?.shutdown() }
    }

    private fun dispatch(action: () -> Unit) {
        handler.post { if (!closed) action() }
    }

    companion object {
        internal fun describe(voice: Voice) = LessonVoice(voice.name, voice.locale.toLanguageTag(),
            voice.isNetworkConnectionRequired, voice.quality)
    }
}
