package com.salesforce.speechsdk.stt

import android.content.Context
import com.salesforce.speechsdk.R
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartLiveSessionFailureTest {
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
    fun `test error codes and messages`() {
        var failure = TranscriptionException.userDeniedPermissionToMicrophone(mockContext)
        assertTrue(failure is TranscriptionException.PermissionException)
        assertEquals(
            mockContext.resources.getString(R.string.speechsdk_user_denied_permission_to_microphone),
            failure.message
        )

        failure = TranscriptionException.sessionInProgress(mockContext)
        assertTrue(failure is TranscriptionException.SessionException)
        assertEquals(
            mockContext.resources.getString(R.string.speechsdk_session_in_progress),
            failure.message
        )

        failure = TranscriptionException.recognitionNotSupported(mockContext)
        assertTrue(failure is TranscriptionException.RecognitionNotSupportedException)
        assertEquals(
            mockContext.resources.getString(R.string.speechsdk_recognition_not_supported),
            failure.message
        )

        failure = TranscriptionException.onDeviceRecognitionNotSupported(mockContext)
        assertTrue(failure is TranscriptionException.OnDeviceRecognitionNotSupportedException)
        assertEquals(
            mockContext.resources.getString(R.string.speechsdk_on_device_recognition_not_supported),
            failure.message
        )

        failure = TranscriptionException.genericError("test message", Exception("test exception"))
        assertTrue(failure is TranscriptionException.GenericException)
        assertEquals("test message", failure.message)
        assertEquals("test exception", failure.cause?.message)
    }
}
