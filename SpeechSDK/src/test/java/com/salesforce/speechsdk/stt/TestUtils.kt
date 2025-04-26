package com.salesforce.speechsdk.stt

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.salesforce.speechsdk.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlin.time.Duration.Companion.milliseconds

internal class TestUtils {
    companion object {
        private lateinit var mockContext: Context
        private lateinit var mockResources: Resources

        fun getMockContextWithMockStringResources(
            relaxed: Boolean = false,
            relaxUnitFun: Boolean = false
        ): Context {
            mockContext = mockk(relaxed = relaxed, relaxUnitFun = relaxUnitFun)
            mockResources = mockk(relaxed = relaxed, relaxUnitFun = relaxUnitFun)
            every { mockContext.applicationContext } returns mockContext
            every { mockContext.resources } returns mockResources
            // Simulate unit tests using the same strings as the production code
            every { mockResources.getString(R.string.speechsdk_microphone_default_rationale_text) } returns "To record audio, permission is needed to access your microphone. Please tap Allow in the following permissions dialog."
            every { mockResources.getString(R.string.speechsdk_microphone_permission_required) } returns "Microphone permission is required."
            every { mockResources.getString(R.string.speechsdk_cancel) } returns "Cancel"
            every { mockResources.getString(R.string.speechsdk_proceed) } returns "Proceed"
            every { mockResources.getString(R.string.speechsdk_user_denied_permission_to_microphone) } returns "Permission to access microphone is denied."
            every { mockResources.getString(R.string.speechsdk_session_in_progress) } returns "A session is already in progress and needs to be stopped before starting another."
            every { mockResources.getString(R.string.speechsdk_recognition_not_supported) } returns "Speech recognition is not supported by this device."
            every { mockResources.getString(R.string.speechsdk_on_device_recognition_not_supported) } returns "On-Device speech recognition is not supported by this device."

            return mockContext
        }

        fun mockLooper() {
            mockkStatic(Looper::class)
            every { Looper.getMainLooper() } returns mockk { every { thread } returns Thread.currentThread() }
            every { Looper.myLooper() } returns mockk()

            mockkConstructor(Handler::class)
            every { anyConstructed<Handler>().post(any()) } answers { (firstArg() as Runnable).run(); true }
        }

        fun mockPermissions(micPermGranted: Boolean) {
            mockkStatic(ContextCompat::class)

            every {
                ContextCompat.checkSelfPermission(
                    any(),
                    android.Manifest.permission.RECORD_AUDIO
                )
            } returns
                    if (micPermGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

            mockkObject(PermissionActivity)

            every {
                PermissionActivity.Companion.requestMicrophonePermission(
                    any(),
                    any(),
                    any()
                )
            } answers {
                (arg(2) as (Boolean) -> Unit).invoke(micPermGranted)
            }
        }
    }
}

internal class Stopwatch {
    private var startMs: Long? = null

    private var stopMs: Long? = null

    /**
     * The elapsed duration between [start] and [stop] calls.
     *
     * @throws IllegalStateException if [start] or [stop] has not been called yet
     */
    val elapsed
        get() =
            startMs?.let { startMs ->
                stopMs?.let { stopMs ->
                    (stopMs - startMs).milliseconds
                } ?: throw IllegalStateException("Not stopped")
            } ?: throw IllegalStateException("Not started")

    /**
     * Start measuring a duration.
     *
     * @throws [IllegalStateException] if called more than once.
     */
    fun start() {
        if (startMs != null) throw IllegalStateException("Already started")
        startMs = System.currentTimeMillis()
    }

    /**
     * Stop measuring the ongoing duration.
     *
     * @throws [IllegalStateException] if called more than once, or [start] was not called.
     */
    fun stop() {
        if (startMs == null) throw IllegalStateException("Not started")
        if (stopMs != null) throw IllegalStateException("Already stopped")
        stopMs = System.currentTimeMillis()
    }
}

/**
 * Measure and return the elapsed time between calls to [Stopwatch.start] and [Stopwatch.stop].
 * The start and stop calls can be in arbitrary places in the code block, unlike `measureTimeMills`
 * which begins timing immediately.
 *
 * ```
 * stopwatch {
 *    setup()
 *    start()
 *    doWork()
 *    stop()
 *    teardown()
 * }
 * ```
 *
 * @return the elapsed duration
 */
internal suspend fun stopwatch(block: suspend Stopwatch.() -> Unit) =
    Stopwatch().let {
        block(it)
        it.elapsed
    }