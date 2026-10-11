package com.quocdat.lingolens.ui.screens

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Composable
fun PronunciationButton(word: String) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Đang khởi tạo giọng đọc…") }
    DisposableEffect(context, lifecycle) {
        val handler = Handler(Looper.getMainLooper())
        var disposed = false
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            handler.post {
                if (!disposed) {
                    val language = if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US) else TextToSpeech.ERROR
                    ready = language >= TextToSpeech.LANG_AVAILABLE
                    message = if (ready) "Giọng đọc tiếng Anh của thiết bị" else "Thiết bị chưa có giọng tiếng Anh. Cài giọng đọc trong cài đặt Text-to-Speech."
                }
            }
        }
        engine = tts
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { handler.post { if (!disposed) speaking = true } }
            override fun onDone(id: String?) { handler.post { if (!disposed) speaking = false } }
            @Deprecated("Required by platform")
            override fun onError(id: String?) { handler.post { if (!disposed) { speaking = false; message = "Không phát được âm thanh. Kiểm tra giọng đọc của thiết bị." } } }
        })
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { tts.stop(); speaking = false }
        }
        lifecycle.addObserver(observer)
        onDispose { disposed = true; lifecycle.removeObserver(observer); handler.removeCallbacksAndMessages(null); tts.stop(); tts.shutdown(); engine = null }
    }
    LaunchedEffect(word) { engine?.stop(); speaking = false }
    Button(enabled = ready, onClick = {
        if (speaking) { engine?.stop(); speaking = false }
        else if (engine?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word-$word") == TextToSpeech.ERROR) {
            message = "Không phát được âm thanh. Kiểm tra giọng đọc của thiết bị."
        }
    }) { Text(if (speaking) "Dừng phát âm" else "Phát âm") }
    Text(message, style = MaterialTheme.typography.bodySmall)
    if (!ready && engine != null) TextButton(onClick = {
        val install = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            engine?.defaultEngine?.let { setPackage(it) }
        }
        try { activityContext.startActivity(install) }
        catch (_: android.content.ActivityNotFoundException) {
            activityContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }) { Text("Cài giọng đọc tiếng Anh") }
}
