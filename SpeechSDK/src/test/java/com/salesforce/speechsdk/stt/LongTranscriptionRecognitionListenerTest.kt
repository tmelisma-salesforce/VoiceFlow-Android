package com.salesforce.speechsdk.stt

import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent.EXTRA_ENABLE_FORMATTING
import android.speech.RecognizerIntent.EXTRA_LANGUAGE
import android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL
import android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS
import android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE
import android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
import android.speech.RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
import android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun String.asPartialResults() =
    Bundle().also {
        it.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(this))
    }

fun String.asFinalResults() =
    Bundle().also {
        it.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(this))
        it.putBoolean("final_result", true)
    }

private fun recognitionListenerFlow(
    languageTag: String,
    silenceLength: Duration,
    block: (LongTranscriptionRecognitionListener) -> Unit
) = callbackFlow {
    block(
        LongTranscriptionRecognitionListener(
            languageTag,
            silenceLength,
            updateResults = {
                trySend(it)
            },
            endSpeech = {
                close()
            }
        )
    )
    awaitClose()
}

@RunWith(RobolectricTestRunner::class)
class LongTranscriptionRecognitionListenerTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(3)

    val testLangTag = "en-US"
    val testSilenceLength = 1.seconds

    @Test
    fun `Speech intent has correct action and extras`() {
        val recognitionListener =
            LongTranscriptionRecognitionListener(
                languageTag = testLangTag,
                silenceLength = testSilenceLength,
                updateResults = {},
                endSpeech = {}
            )
        val intent = recognitionListener.speechIntent()
        assertEquals(
            LANGUAGE_MODEL_FREE_FORM,
            intent.getStringExtra(EXTRA_LANGUAGE_MODEL)
        )
        assertEquals(
            testLangTag,
            intent.getStringExtra(EXTRA_LANGUAGE)
        )
        assertEquals(
            testSilenceLength.inWholeMilliseconds.toInt(),
            intent.getIntExtra(EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0)
        )
        assertEquals(
            true,
            intent.getBooleanExtra(EXTRA_PARTIAL_RESULTS, false)
        )
        assertEquals(
            true,
            intent.getBooleanExtra(EXTRA_PREFER_OFFLINE, false)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertEquals(
                FORMATTING_OPTIMIZE_QUALITY,
                intent.getStringExtra(EXTRA_ENABLE_FORMATTING)
            )
        }
    }

    @Test
    fun `Voice recognition ends after complete silence has elapsed`() {
        runBlocking {
            var actual: List<String?> = emptyList()
            val elapsed =
                stopwatch {
                    actual =
                        recognitionListenerFlow(testLangTag, testSilenceLength) {
                            it.onReadyForSpeech(null)
                            it.onBeginningOfSpeech()
                            it.onPartialResults("Testing".asPartialResults())
                            it.onEndOfSpeech()
                            // A timer is started after each onEndOfSpeech call to determine if a
                            // complete silence has occurred
                            start()
                            it.onPartialResults("Testing".asFinalResults())
                        }.toList()
                    stop()
                }
            assertTrue(
                elapsed > testSilenceLength && elapsed < testSilenceLength + 1000.milliseconds
            )
            assertEquals(
                listOf(
                    "Testing",
                    "Testing"
                ),
                actual
            )
        }
    }

    @Test
    fun `Multiple partial and final results concatenated as expected`() {
        runBlocking {
            val actual =
                recognitionListenerFlow(testLangTag, testSilenceLength) {
                    it.onReadyForSpeech(null)
                    it.onBeginningOfSpeech()
                    it.onPartialResults("Wreck a nice beach".asPartialResults())
                    it.onEndOfSpeech()
                    it.onPartialResults("Recognize speech".asFinalResults())
                    it.onBeginningOfSpeech()
                    it.onPartialResults("You sing calm incense".asPartialResults())
                    it.onEndOfSpeech()
                    it.onResults("Using common sense".asFinalResults())
                }.toList()
            assertEquals(
                listOf(
                    "Wreck a nice beach",
                    "Recognize speech",
                    "Recognize speech\nYou sing calm incense",
                    "Recognize speech\nUsing common sense"
                ),
                actual
            )
        }
    }

    @Test
    fun `A partial result is received after onEndOfSpeech but before onBeginningOfSpeech`() {
        runBlocking {
            val actual =
                recognitionListenerFlow(testLangTag, testSilenceLength) {
                    it.onReadyForSpeech(null)
                    it.onBeginningOfSpeech()
                    it.onPartialResults("Wreck a nice beach".asPartialResults())
                    it.onEndOfSpeech()
                    it.onPartialResults("Recognize speech".asFinalResults())
                    it.onBeginningOfSpeech()
                    it.onPartialResults("You sing calm incense".asPartialResults())
                    it.onPartialResults("Using common sense".asFinalResults())
                    it.onEndOfSpeech()
                }.toList()
            assertEquals(
                listOf(
                    "Wreck a nice beach",
                    "Recognize speech",
                    "Recognize speech\nYou sing calm incense",
                    "Recognize speech\nUsing common sense"
                ),
                actual
            )
        }
    }

    @Test
    fun `onResults is called instead of onPartialResults`() {
        runBlocking {
            val actual =
                recognitionListenerFlow(testLangTag, testSilenceLength) {
                    it.onReadyForSpeech(null)
                    it.onBeginningOfSpeech()
                    it.onPartialResults("Wreck a nice beach".asPartialResults())
                    it.onEndOfSpeech()
                    it.onResults("Recognize speech".asFinalResults())
                    it.onBeginningOfSpeech()
                    it.onPartialResults("You sing calm incense".asPartialResults())
                    it.onResults("Using common sense".asFinalResults())
                    it.onEndOfSpeech()
                }.toList()
            assertEquals(
                listOf(
                    "Wreck a nice beach",
                    "Recognize speech",
                    "Recognize speech\nYou sing calm incense",
                    "Recognize speech\nUsing common sense"
                ),
                actual
            )
        }
    }

    @Test
    fun `The last partial result is sent after complete silence timeout`() {
        runBlocking {
            val actual =
                recognitionListenerFlow(testLangTag, testSilenceLength) {
                    it.onReadyForSpeech(null)
                    it.onBeginningOfSpeech()
                    it.onPartialResults("Wreck a nice beach".asPartialResults())
                    it.onEndOfSpeech()
                    it.onPartialResults("Recognize speech".asFinalResults())
                }.toList()
            assertEquals(
                listOf(
                    "Wreck a nice beach",
                    "Recognize speech"
                ),
                actual
            )
        }
    }

    @Test
    fun `A missing final_results extra will prevent partial results from being added to the history`() {
        runBlocking {
            val actual =
                recognitionListenerFlow(testLangTag, testSilenceLength) {
                    it.onReadyForSpeech(null)
                    it.onBeginningOfSpeech()
                    it.onPartialResults("Wreck a nice beach".asPartialResults())
                    it.onEndOfSpeech()
                    it.onPartialResults("Recognize speech".asPartialResults())
                    it.onBeginningOfSpeech()
                    it.onBeginningOfSpeech()
                    it.onPartialResults("You sing calm incense".asPartialResults())
                    it.onEndOfSpeech()
                    it.onPartialResults("Using common sense".asPartialResults())
                }.toList()
            assertEquals(
                listOf(
                    "Wreck a nice beach",
                    "Recognize speech",
                    "You sing calm incense",
                    "Using common sense"
                ),
                actual
            )
        }
    }

    @Test
    fun `Concat partial results`() {
        assertEquals(
            "Testing",
            concatPartialResults(history = null, partialResults = "Testing")
        )
        assertEquals(
            "Testing",
            concatPartialResults(history = "Testing", partialResults = null)
        )
        assertEquals(
            "Testing 1\nTesting 2",
            concatPartialResults(history = "Testing 1", partialResults = "Testing 2")
        )
        assertEquals(
            null,
            concatPartialResults(history = null, partialResults = null)
        )
    }

    @Test
    fun `endSpeech called on error`() {
        val possibleErrors = listOf(
            Pair(SpeechRecognizer.ERROR_AUDIO, "ERROR_AUDIO"),
            Pair(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT, "ERROR_CANNOT_CHECK_SUPPORT"),
            Pair(SpeechRecognizer.ERROR_CLIENT, "ERROR_CLIENT"),
            Pair(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, "ERROR_INSUFFICIENT_PERMISSIONS"),
            Pair(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, "ERROR_LANGUAGE_NOT_SUPPORTED"),
            Pair(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, "ERROR_LANGUAGE_UNAVAILABLE"),
            Pair(SpeechRecognizer.ERROR_NETWORK, "ERROR_NETWORK"),
            Pair(SpeechRecognizer.ERROR_NETWORK_TIMEOUT, "ERROR_NETWORK_TIMEOUT"),
            Pair(SpeechRecognizer.ERROR_NO_MATCH, "ERROR_NO_MATCH"),
            Pair(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, "ERROR_RECOGNIZER_BUSY"),
            Pair(SpeechRecognizer.ERROR_SERVER, "ERROR_SERVER"),
            Pair(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, "ERROR_SERVER_DISCONNECTED"),
            Pair(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, "ERROR_SPEECH_TIMEOUT"),
            Pair(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, "ERROR_TOO_MANY_REQUESTS"),
            Pair(1234, "1234")
        )

        possibleErrors.map { failurePair ->
            var failure: TranscriptionException? = null
            LongTranscriptionRecognitionListener(
                testLangTag, testSilenceLength,
                updateResults = {},
                endSpeech = { failure = it },
            ).onError(failurePair.first)
            assertEquals(
                "SpeechRecognizer error, code = ${failurePair.second}",
                failure?.message
            )
        }
    }
}
