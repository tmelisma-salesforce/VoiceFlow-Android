package com.salesforce.speechsdk.stt

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PermissionActivityTest {
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
    fun `Returns TRUE when perm granted`() {
        val latch = CountDownLatch(1)
        var granted = false
        val permActivity = PermissionActivity()

        every { mockContext.startActivity(any()) } answers {
            permActivity.handleActivityResults(true)
        }

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), android.Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        PermissionActivity.requestMicrophonePermission(mockContext) {
            granted = it; latch.countDown()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS)) // wait and ensure we didn't timeout
        assertTrue(granted)
    }

    @Test
    fun `Returns FALSE when perm not granted`() {
        val latch = CountDownLatch(1)
        var granted = true
        val permActivity = PermissionActivity()

        every { mockContext.startActivity(any()) } answers {
            permActivity.handleActivityResults(false)
        }
        every { mockContext.theme } returns mockk(relaxed = true)

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), android.Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_DENIED

        PermissionActivity.requestMicrophonePermission(mockContext) {
            granted = it; latch.countDown()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS)) // wait and ensure we didn't timeout
        assertFalse(granted)
    }
}