package com.salesforce.speechsdk.stt

import android.content.Context
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SpeechToTextManagerTest {
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = TestUtils.getMockContextWithMockStringResources()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `creates the default platform fallback STT service and initializes it`() {
        val sttManager = SpeechToTextManager(
            mockContext,
            model = TranscriptionModel.OS,
            permissionRationaleText = "Test Perm Rationale"
        )
        sttManager.setLanguage("it-IT")
        sttManager.setSilenceLength(10.seconds)

        assertTrue(sttManager.state.value is TranscriptionState.Initialized)
        assertEquals("", sttManager.text.value)

        val internalService = sttManager.speechToText as? OSSpeechToTextService
        assertEquals("it-IT", internalService?.languageTag)
        assertEquals(10.seconds, internalService?.silenceLength)
    }
}