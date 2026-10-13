package com.quocdat.lingolens.lesson

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LessonVoice(
    val name: String,
    val languageTag: String,
    val requiresNetwork: Boolean,
    val quality: Int = 0
)

sealed interface LessonSpeechEvent {
    val utteranceId: String
    data class Started(override val utteranceId: String) : LessonSpeechEvent
    data class Completed(override val utteranceId: String) : LessonSpeechEvent
    data class Failed(override val utteranceId: String) : LessonSpeechEvent
}

interface LessonSpeechEngine {
    val defaultEnginePackage: String?
    fun initialize(onInitialized: (Boolean) -> Unit, onUtterance: (LessonSpeechEvent) -> Unit)
    fun availableVoices(): List<LessonVoice>
    fun speak(text: String, voice: LessonVoice, utteranceId: String): Boolean
    fun stop()
    fun close()
}

enum class LessonSpeechStatus { INITIALIZING, READY, MISSING_VOICE, NETWORK_REQUIRED, ERROR, CLOSED }

data class LessonSpeechState(
    val status: LessonSpeechStatus = LessonSpeechStatus.INITIALIZING,
    val selectedVoice: LessonVoice? = null,
    val speaking: Boolean = false,
    val error: String? = null
)

/** Main-thread controller; only speech state, never lesson/session/grading state. */
class LessonSpeechController(private val engine: LessonSpeechEngine, online: Boolean = true) {
    private val mutableState = MutableStateFlow(LessonSpeechState())
    val state: StateFlow<LessonSpeechState> = mutableState.asStateFlow()
    val defaultEnginePackage: String? get() = engine.defaultEnginePackage
    private var online = online
    private var started = false
    private var initializationFinished = false
    private var initialized = false
    private var closed = false
    private var generation = 0L
    private var activeUtterance: String? = null

    fun start() {
        if (started || closed) return
        started = true
        try {
            engine.initialize({ success ->
                if (!closed && !initializationFinished) {
                    initializationFinished = true
                    initialized = success
                    if (success) refreshVoices(online)
                    else fail("Không khởi tạo được giọng đọc. Bạn có thể dùng câu thay thế.")
                }
            }, ::onUtterance)
        } catch (_: RuntimeException) {
            initializationFinished = true
            fail("Không khởi tạo được giọng đọc. Bạn có thể dùng câu thay thế.")
        }
    }

    /** Call on foreground/resume and after returning from the voice installer. */
    fun refreshVoices(online: Boolean) {
        this.online = online
        if (closed || !initialized) return
        stop()
        val available = try { engine.availableVoices().filter(::isUsEnglish) }
        catch (_: RuntimeException) {
            fail("Không đọc được danh sách giọng. Bạn có thể dùng câu thay thế.")
            return
        }
        val order = compareByDescending<LessonVoice> { it.quality }.thenBy { it.name }
        val selected = available.filter { !it.requiresNetwork }.sortedWith(order).firstOrNull()
            ?: if (online) available.sortedWith(order).firstOrNull() else null
        mutableState.value = when {
            selected != null -> LessonSpeechState(LessonSpeechStatus.READY, selected)
            available.isNotEmpty() -> LessonSpeechState(LessonSpeechStatus.NETWORK_REQUIRED,
                error = "Giọng en-US hiện có cần mạng. Hãy cài giọng dùng ngoại tuyến hoặc dùng câu thay thế.")
            else -> LessonSpeechState(LessonSpeechStatus.MISSING_VOICE,
                error = "Máy chưa có giọng en-US. Hãy cài giọng hoặc dùng câu thay thế.")
        }
    }

    fun speak(text: String): Boolean {
        val voice = mutableState.value.selectedVoice
        if (closed || mutableState.value.status != LessonSpeechStatus.READY || voice == null || text.isBlank()) return false
        stop()
        val id = "lesson-${++generation}"
        activeUtterance = id
        mutableState.value = mutableState.value.copy(speaking = true, error = null)
        val accepted = try { engine.speak(text, voice, id) } catch (_: RuntimeException) { false }
        if (!accepted && activeUtterance == id) {
            activeUtterance = null
            fail("Không phát được giọng đọc. Bạn có thể thử lại hoặc dùng câu thay thế.")
        }
        return accepted
    }

    /** Call on background, navigation away, question switch, and account switch. */
    fun stop() {
        if (closed) return
        activeUtterance = null
        generation++
        runCatching { engine.stop() }
        mutableState.value = mutableState.value.copy(speaking = false)
    }

    fun close() {
        if (closed) return
        activeUtterance = null
        generation++
        closed = true
        runCatching { engine.stop() }
        runCatching { engine.close() }
        mutableState.value = LessonSpeechState(LessonSpeechStatus.CLOSED)
    }

    private fun onUtterance(event: LessonSpeechEvent) {
        if (closed || activeUtterance != event.utteranceId) return
        when (event) {
            is LessonSpeechEvent.Started -> mutableState.value = mutableState.value.copy(speaking = true)
            is LessonSpeechEvent.Completed -> {
                activeUtterance = null
                mutableState.value = mutableState.value.copy(speaking = false)
            }
            is LessonSpeechEvent.Failed -> {
                activeUtterance = null
                fail("Giọng đọc bị lỗi. Bạn có thể thử lại hoặc dùng câu thay thế.")
            }
        }
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(status = LessonSpeechStatus.ERROR, speaking = false, error = message)
    }

    private fun isUsEnglish(voice: LessonVoice): Boolean {
        val locale = Locale.forLanguageTag(voice.languageTag)
        return locale.language == "en" && locale.country == "US"
    }
}
