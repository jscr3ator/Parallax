package app.parallax.opencode.ui.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * On-device voice I/O: Android's SpeechRecognizer (live partial transcripts + mic level) and TextToSpeech.
 * No network service of its own — what you say becomes a normal prompt to your opencode agent.
 * All calls must happen on the main thread (the platform requires it); callbacks arrive on main too.
 */
class VoiceEngine(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 0..1 smoothed mic loudness while listening (drives the orb). */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Words recognized so far in the current utterance. */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(app)

    /** High-quality voices for the current language, best first. */
    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()
    private val _voiceName = MutableStateFlow<String?>(null)
    val voiceName: StateFlow<String?> = _voiceName.asStateFlow()
    private val prefs = app.getSharedPreferences("parallax_voice", Context.MODE_PRIVATE)

    /** Utterances queued with [enqueue] that haven't finished yet, and what to run when they all have. */
    private val queued = linkedMapOf<String, String>()
    private val _caption = MutableStateFlow("")
    val caption = _caption.asStateFlow()
    private var directId: String? = null
    private var initFailed = false
    private var onQueueDrained: (() -> Unit)? = null

    private var listeningGeneration = 0
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: Pair<String, () -> Unit>? = null

    init {
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            initFailed = !ttsReady
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.10f)
                tts?.setPitch(1.0f)
                loadVoices()
                queued.toMap().forEach { (id, text) -> submitQueued(id, text) }
            } else {
                queued.keys.toList().forEach(::utteranceFinished)
            }
            pendingSpeech?.let { (text, done) ->
                pendingSpeech = null
                speak(text, done)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { updateCaption(utteranceId, 0) } }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                main.post { updateCaption(utteranceId, start) }
            }
            override fun onDone(utteranceId: String?) { main.post { utteranceFinished(utteranceId) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { utteranceFinished(utteranceId) } }
        })
    }

    private var onSpeechDone: (() -> Unit)? = null

    private fun updateCaption(id: String?, offset: Int) {
        val text = queued[id] ?: return
        _caption.value = captionPage(text, offset)
    }

    private fun utteranceFinished(id: String?) {
        if (id != null && id.startsWith(Q_PREFIX)) {
            if (queued.remove(id) == null) return
            if (queued.isEmpty()) {
                _speaking.value = false
                onQueueDrained?.let { cb -> onQueueDrained = null; cb() }
            }
            return
        }
        if (id == null || id != directId) return
        _speaking.value = false
        directId = null
        onSpeechDone?.let { cb -> onSpeechDone = null; cb() }
    }

    /** Add a sentence to the speech queue without interrupting what's already being said (streaming replies). */
    fun enqueue(text: String) {
        if (text.isBlank() || initFailed) return
        val id = Q_PREFIX + UUID.randomUUID()
        queued[id] = text
        _speaking.value = true
        if (ttsReady) submitQueued(id, text)
    }

    private fun submitQueued(id: String, text: String) {
        if (tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) utteranceFinished(id)
    }

    /** Run [onDone] once everything queued with [enqueue] has been spoken (immediately if nothing is queued). */
    fun whenQueueDrained(onDone: () -> Unit) {
        if (queued.isEmpty()) { _speaking.value = false; onDone() } else onQueueDrained = onDone
    }

    /** Pick the most natural-sounding voice: user choice first, else the highest-quality installed voice. */
    private fun loadVoices() {
        val engine = tts ?: return
        val lang = Locale.getDefault().language
        val country = Locale.getDefault().country
        val all = runCatching { engine.voices?.toList() }.getOrNull().orEmpty()
            .filter { it.locale.language == lang && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        val network = app.getSystemService(ConnectivityManager::class.java)
        val online = network.getNetworkCapabilities(network.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        fun score(v: android.speech.tts.Voice): Int {
            var sc = v.quality
            if (v.isNetworkConnectionRequired) sc += if (online) 125 else -1000
            if (v.locale.country == country) sc += 25
            sc -= v.latency / 2
            return sc
        }
        val ranked = all.sortedByDescending { score(it) }
        _voices.value = ranked.mapIndexed { i, v -> VoiceOption(v.name, voiceLabel(v, i), v.isNetworkConnectionRequired || v.name.contains("network")) }
        val chosen = prefs.getString("voice", null)?.let { name -> ranked.find { it.name == name } } ?: ranked.firstOrNull()
        chosen?.let { engine.voice = it; _voiceName.value = it.name }
        android.util.Log.i("ParallaxVoice", "voices=${ranked.size} chosen=${chosen?.name} q=${chosen?.quality} net=${chosen?.isNetworkConnectionRequired} top=${ranked.take(5).joinToString { it.name }}")
    }

    fun selectVoice(name: String) {
        val v = runCatching { tts?.voices?.find { it.name == name } }.getOrNull() ?: return
        tts?.voice = v
        _voiceName.value = name
        prefs.edit().putString("voice", name).apply()
    }

    /** Say a short sample in [name] (used by the voice picker). */
    fun preview(name: String) {
        selectVoice(name)
        speak("Hi, I'm your agent. Tell me what to build and I'll get to work.")
    }

    /**
     * Listen for one utterance. [onResult] gets the final text ("" if nothing was heard);
     * [onError] gets a short human message for real failures (no permission, no recognizer).
     */
    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        stopSpeaking()
        if (!available) { onError("Speech recognition isn't available on this phone."); return }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(app).also { recognizer = it }
        _partial.value = ""
        val generation = ++listeningGeneration
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (generation != listeningGeneration) return
                // rmsdB is roughly -2..10; map to 0..1 and smooth so the orb breathes instead of jittering.
                val target = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _level.value = _level.value * 0.6f + target * 0.4f
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (generation == listeningGeneration) _level.value = 0f }
            override fun onError(error: Int) {
                if (generation != listeningGeneration) return
                _level.value = 0f
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onResult("")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onError("Parallax needs microphone permission.")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> main.postDelayed({ if (generation == listeningGeneration) listen(onResult, onError) }, 400)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> onError("Speech recognition needs a connection right now.")
                    else -> onResult("")
                }
            }
            override fun onResults(results: Bundle?) {
                if (generation != listeningGeneration) return
                _level.value = 0f
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _partial.value = text
                onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (generation != listeningGeneration) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { _partial.value = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            // End the turn quickly once you stop talking.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 520L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 420L)
        }
        r.startListening(intent)
    }

    fun stopListening() {
        listeningGeneration++
        recognizer?.cancel()
        _level.value = 0f
    }

    /** Speak [text]; [onDone] runs when it finishes or is interrupted. */
    fun speak(text: String, onDone: () -> Unit = {}) {
        if (initFailed) { onDone(); return }
        if (!ttsReady) { pendingSpeech = text to onDone; return }
        onSpeechDone = onDone
        _speaking.value = true
        val id = UUID.randomUUID().toString()
        directId = id
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) utteranceFinished(id)
    }

    fun stopSpeaking() {
        onSpeechDone = null
        onQueueDrained = null
        queued.clear()
        directId = null
        pendingSpeech = null
        _caption.value = ""
        tts?.stop()
        _speaking.value = false
    }

    fun release() {
        stopListening()
        recognizer?.destroy(); recognizer = null
        stopSpeaking(); tts?.shutdown(); tts = null
    }
}

data class VoiceOption(val name: String, val label: String, val natural: Boolean)

private const val Q_PREFIX = "q-"

private fun voiceLabel(v: android.speech.tts.Voice, index: Int): String {
    val region = v.locale.getDisplayCountry().ifBlank { v.locale.getDisplayLanguage() }
    val kind = if (v.isNetworkConnectionRequired || v.name.contains("network")) "Natural" else "On-device"
    return "$region · $kind ${index + 1}"
}

/** Turn markdown into something pleasant to hear: no code blocks, symbols or URLs; capped length. */
fun speakable(markdown: String, max: Int = 650): String {
    var t = markdown
        .replace(Regex("```[\\s\\S]*?```"), " (code is in the chat) ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("https?://\\S+"), "a link")
        .replace(Regex("""(?m)^[ \t]*[-*+]\s+\[[ xX]\][ \t]+"""), "")
        .replace(Regex("""(?m)^[ \t]*\|?[ \t]*:?-{3,}:?[ \t]*(?:\|[ \t]*:?-{3,}:?[ \t]*)*\|?[ \t]*$"""), "")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("[*_~>|]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (t.length > max) {
        val cut = t.take(max)
        val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("? "), cut.lastIndexOf("! "))
        t = (if (end > max / 2) cut.take(end + 1) else cut) + " The rest is in the chat."
    }
    return t
}

/** Keep a stable page until playback crosses its final word. */
internal fun captionPage(text: String, offset: Int): String {
    var start = 0
    while (text.length - start > 78) {
        val space = text.lastIndexOf(' ', start + 78)
        val end = if (space > start) space + 1 else start + 78
        if (offset < end) return text.substring(start, end).trim()
        start = end
    }
    return text.substring(start).trim()
}
