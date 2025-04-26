package com.salesforce.speechsdk.stt

import android.content.Context
import android.os.Bundle
import android.speech.SpeechRecognizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class OSSpeechToTextServiceTest {
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = TestUtils.getMockContextWithMockStringResources()
        TestUtils.mockLooper()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `STT fails when mic perm not granted`() {
        TestUtils.mockPermissions(micPermGranted = false)

        val stt = OSSpeechToTextService(mockContext)
        stt.startTranscribing()

        assertEquals("", stt.text.value)
        assertTrue(
            (stt.state.value as? TranscriptionState.Failed)?.exception is TranscriptionException.PermissionException
        )
    }

    @Test
    fun `STT fails when recognition is not available`() {
        TestUtils.mockPermissions(micPermGranted = true)
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns false

        val stt = OSSpeechToTextService(mockContext)
        stt.startTranscribing()

        assertEquals("", stt.text.value)
        assertTrue(
            (stt.state.value as? TranscriptionState.Failed)?.exception is TranscriptionException.RecognitionNotSupportedException
        )
    }

    @Test
    fun `STT fails when on-device recognition is not available`() {
        TestUtils.mockPermissions(micPermGranted = true)
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true

        val stt = spyk(OSSpeechToTextService(mockContext)) {
            every { isOnDeviceRecognitionAvailable() } returns false
        }
        stt.startTranscribing()

        assertEquals("", stt.text.value)
        assertTrue(
            (stt.state.value as? TranscriptionState.Failed)?.exception is TranscriptionException.OnDeviceRecognitionNotSupportedException
        )
    }

    @Test
    fun `STT return error when trying to start 2 live sessions simultaneously`() {
        TestUtils.mockPermissions(micPermGranted = true)

        mockkConstructor(LongTranscriptionRecognitionListener::class)
        every { anyConstructed<LongTranscriptionRecognitionListener>().speechIntent() } returns mockk(
            relaxed = true
        )

        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        every { SpeechRecognizer.createOnDeviceSpeechRecognizer(any()) } returns mockk(relaxed = true)

        val stt = spyk(OSSpeechToTextService(mockContext)) {
            every { isOnDeviceRecognitionAvailable() } returns true
        }

        // start first session => Ready state
        stt.startTranscribing()
        assertEquals("", stt.text.value)
        assertTrue(stt.state.value is TranscriptionState.Ready)

        // start another session => Failed state
        stt.startTranscribing()
        assertEquals("", stt.text.value)
        assertTrue(
            (stt.state.value as? TranscriptionState.Failed)?.exception is TranscriptionException.SessionException
        )
    }

    @Test
    fun `STT recognizes text`() {
        val sampleText = "This is a test."
        lateinit var recognitionListener: LongTranscriptionRecognitionListener

        TestUtils.mockPermissions(micPermGranted = true)

        mockkConstructor(LongTranscriptionRecognitionListener::class)
        every { anyConstructed<LongTranscriptionRecognitionListener>().speechIntent() } returns mockk(
            relaxed = true
        )

        mockkConstructor(Bundle::class)
        every {
            anyConstructed<Bundle>().putStringArrayList(
                any(),
                any()
            )
        } returns mockk(relaxed = true)
        every { anyConstructed<Bundle>().putBoolean(any(), any()) } returns mockk(relaxed = true)
        every { anyConstructed<Bundle>().getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf(
            sampleText
        )
        every { anyConstructed<Bundle>().getBoolean("final_result") } returns true

        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        every { SpeechRecognizer.createOnDeviceSpeechRecognizer(any()) } returns mockk(relaxed = true) {
            every { setRecognitionListener(any()) } answers {
                recognitionListener = firstArg()
            }
        }

        val stt = spyk(OSSpeechToTextService(mockContext)) {
            every { isOnDeviceRecognitionAvailable() } returns true
        }

        stt.startTranscribing()
        assertTrue(stt.state.value is TranscriptionState.Ready)

        recognitionListener.onResults(sampleText.asFinalResults())
        recognitionListener.onEndOfSpeech()
        assertTrue(stt.state.value is TranscriptionState.Processing)

        stt.stopTranscribing()
        assertTrue(stt.state.value is TranscriptionState.Finished)
        assertEquals(sampleText, stt.text.value)
    }
}