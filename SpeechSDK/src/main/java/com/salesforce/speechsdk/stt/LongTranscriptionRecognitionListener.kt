package com.salesforce.speechsdk.stt

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private enum class RecognitionState {
    Created,
    ReadyForSpeech,
    BeginningOfSpeech,
    EndOfSpeech,
}

internal class LongTranscriptionRecognitionListener(
    private val languageTag: String = Locale.getDefault().toLanguageTag(),
    private val silenceLength: Duration = 30.seconds,
    private val updateResults: (result: String?) -> Unit,
    private val endSpeech: (failure: TranscriptionException?) -> Unit,
) : RecognitionListener, AutoCloseable {
    private val timerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val state = MutableStateFlow(RecognitionState.Created)

    private var history: String? = null

    private var lastPartialResults: String? = null

    init {
        timerScope.launch {
            state
                .collectLatest {
                    if (it == RecognitionState.EndOfSpeech) {
                        delay(silenceLength.inWholeMilliseconds)
                        endSpeech(null)
                    }
                }
        }
    }

    override fun close() {
        try {
            // attempt at cancelling the timer and all of its jobs
            timerScope.cancel()
        } catch (_: Exception) {
            // ignore and continue
        }
    }

    fun speechIntent() =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                languageTag
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                silenceLength.inWholeMilliseconds.toInt()
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )
            putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Enable text formatting (e.g. unspoken punctuation, capitalization, etc.)
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
                )
            }
        }

    override fun onReadyForSpeech(bundle: Bundle?) {
        Log.d(TAG, "RecognitionListener - ready to analyze speech")
        resetHistory()
        state.value = RecognitionState.ReadyForSpeech
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "RecognitionListener - beginning of speech detected")
        state.value = RecognitionState.BeginningOfSpeech
    }

    override fun onRmsChanged(v: Float) {
        // do nothing
    }

    override fun onBufferReceived(bytes: ByteArray?) {
        // do nothing
    }

    override fun onEvent(i: Int, bundle: Bundle?) {
        // do nothing
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "RecognitionListener - end of speech detected")
        state.value = RecognitionState.EndOfSpeech
    }

    override fun onError(error: Int) {
        val errCode = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
            else -> "$error"
        }
        endSpeech(TranscriptionException.genericError("SpeechRecognizer error, code = $errCode"))
    }

    override fun onResults(bundle: Bundle?) {
        processResult(bundle)
    }

    override fun onPartialResults(bundle: Bundle?) {
        processResult(bundle)
    }

    private fun processResult(bundle: Bundle?) {
        bundle.firstRecognitionResult?.let { result ->
            if (bundle.isFinalResult) {
                lastPartialResults = result
                updateHistory()
            } else {
                concatPartialResults(history, result)?.let {
                    updateResults(it)
                }
            }
        }
    }

    private fun resetHistory() {
        history = null
        lastPartialResults = null
    }

    private fun updateHistory() {
        concatPartialResults(history, lastPartialResults)?.let {
            updateResults(it)
            history = it
        }
        lastPartialResults = null
    }

    companion object {
        const val TAG = "LongTranscriptionRecognitionListener"
    }
}

@VisibleForTesting
internal fun concatPartialResults(
    history: String?,
    partialResults: String?
) = when {
    history != null && partialResults != null -> "${history}\n$partialResults"
    history != null && partialResults == null -> history
    history == null && partialResults != null -> partialResults
    else -> null
}

private val Bundle?.firstRecognitionResult
    get() = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

private val Bundle?.isFinalResult
    get() = this?.getBoolean("final_result") == true