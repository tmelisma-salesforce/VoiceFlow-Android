package com.salesforce.speechsdk.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.ERROR
import android.speech.tts.TextToSpeech.LANG_AVAILABLE
import android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE
import android.speech.tts.TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
import android.speech.tts.TextToSpeech.SUCCESS
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackException.ERROR_CODE_INVALID_STATE
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.exoplayer.ExoPlayer
import com.salesforce.speechsdk.stt.TestUtils
import com.salesforce.speechsdk.tts.SynthesizerState.Failed
import com.salesforce.speechsdk.tts.SynthesizerState.Finished
import com.salesforce.speechsdk.tts.SynthesizerState.Paused
import com.salesforce.speechsdk.tts.SynthesizerState.Processing
import com.salesforce.speechsdk.tts.SynthesizerState.Ready
import com.salesforce.speechsdk.tts.SynthesizerState.Shutdown
import com.salesforce.speechsdk.tts.SynthesizerState.Speaking
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

const val ID = "id"
const val TEXT = "test"
const val MAX_LENGTH = 4000

@Suppress("DEPRECATION")
class OSTextToSpeechServiceTest {
    private lateinit var tts: OSTextToSpeechService
    private val utteranceProgressListener = slot<UtteranceProgressListener>()
    private lateinit var mockkContext: Context
    private lateinit var mockkPlayer: ExoPlayer
    private val exoPlayerListener = slot<Player.Listener>()
    private val fileUtils = spyk(TestFileUtils())

    @Before
    fun setUp() {
        mockkContext = mockk<Context>(relaxed = true)
        mockkPlayer = mockk<ExoPlayer>(relaxed = true)

        TestUtils.mockLooper()
        mockkConstructor(TextToSpeech::class)
        every {
            anyConstructed<TextToSpeech>().setOnUtteranceProgressListener(
                capture(
                    utteranceProgressListener
                )
            )
        } returns SUCCESS
        every { anyConstructed<TextToSpeech>().isSpeaking } returns true
        mockkStatic(TextToSpeech::class)
        every { TextToSpeech.getMaxSpeechInputLength() } returns MAX_LENGTH

        every { mockkPlayer.isPlaying } returns true
        every { mockkPlayer.addListener(capture(exoPlayerListener)) } just runs
        every { mockkPlayer.currentMediaItem } returns mockk()
        every { mockkPlayer.currentMediaItem?.mediaId } returns "test"

        tts = OSTextToSpeechService(mockkContext, mockkPlayer, fileUtils)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Speaking updates service state`() {
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(
                any(),
                any<Bundle>(),
                any<File>(),
                any<String>()
            )
        } returns SUCCESS

        Assert.assertTrue("Initial state should be Ready", tts.state.value is Ready)
        tts.speak(TEXT)
        Assert.assertTrue(tts.state.value is Ready)
        utteranceProgressListener.captured.onStart(ID)
        Assert.assertTrue(tts.state.value is Processing)
        every { mockkPlayer.isPlaying } returns false
        every { mockkPlayer.playbackState } returns STATE_IDLE
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)
        verify(exactly = 1) {
            mockkPlayer.addMediaItem(any())
            mockkPlayer.prepare()
        }
    }

    @Test
    fun `Speaking state stays when more text is added`() {
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(
                any(),
                any<Bundle>(),
                any<File>(),
                any<String>()
            )
        } returns SUCCESS
        every { mockkPlayer.isPlaying } returns false
        every { mockkPlayer.playbackState } returns STATE_IDLE

        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)

        // Add text while speaking
        tts.speak("Another string")
        utteranceProgressListener.captured.onStart(ID)
        Assert.assertTrue(tts.state.value is Speaking)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)

        verify(exactly = 2) { mockkPlayer.addMediaItem(any()) }
    }

    @Test
    fun `Player Buffering and Ready states should be ignored`() {
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(
                any(),
                any<Bundle>(),
                any<File>(),
                any<String>()
            )
        } returns SUCCESS
        every { mockkPlayer.isPlaying } returns false

        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        every { mockkPlayer.playbackState } returns STATE_BUFFERING
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)

        // Add text while speaking
        tts.speak("Another string")
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        every { mockkPlayer.playbackState } returns STATE_READY
        Assert.assertTrue(tts.state.value is Speaking)

        verify(exactly = 2) { mockkPlayer.addMediaItem(any()) }
        verify(exactly = 0) {
            mockkPlayer.prepare()
            mockkPlayer.seekTo(any(), any())
        }
    }

    @Test
    fun `Speaking is not interrupted when new text is added`() {
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(any(), null, any(), any())
        } returns SUCCESS

        Assert.assertTrue("Initial state should be Ready", tts.state.value is Ready)
        tts.speak(TEXT)
        Assert.assertTrue(tts.state.value is Ready)
        utteranceProgressListener.captured.onStart(ID)
        Assert.assertTrue(tts.state.value is Processing)

        every { mockkPlayer.isPlaying } returns true
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)
        verify(exactly = 1) {
            mockkPlayer.addMediaItem(any())
        }
        verify(exactly = 0) {
            mockkPlayer.prepare()
            mockkPlayer.seekTo(any(), any())
        }
    }

    @Test
    fun `Speaking after playlist is finished plays latest file`() {
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(any(), null, any(), any())
        } returns SUCCESS

        Assert.assertTrue("Initial state should be Ready", tts.state.value is Ready)
        tts.speak(TEXT)
        Assert.assertTrue(tts.state.value is Ready)
        utteranceProgressListener.captured.onStart(ID)
        Assert.assertTrue(tts.state.value is Processing)

        val mediaItemCount = 10
        every { mockkPlayer.isPlaying } returns false
        every { mockkPlayer.playbackState } returns STATE_ENDED
        every { mockkPlayer.mediaItemCount } returns mediaItemCount

        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)
        verify(exactly = 1) {
            mockkPlayer.addMediaItem(any())
            mockkPlayer.seekTo((mediaItemCount - 1), C.TIME_UNSET)
        }
    }

    @Test
    fun `null utteranceId leads to failed state`() {
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(
                any(),
                any<Bundle>(),
                any<File>(),
                any<String>()
            )
        } returns SUCCESS

        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        // Send null id
        utteranceProgressListener.captured.onDone(null)
        Assert.assertTrue(tts.state.value is Failed)
        Assert.assertTrue((tts.state.value as Failed).exception is SynthesizerException.ProcessingException)

        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        Assert.assertTrue(tts.state.value is Processing)
        // Send null id
        utteranceProgressListener.captured.onDone(" ") // blank, not null
        Assert.assertTrue(tts.state.value is Failed)
        Assert.assertTrue((tts.state.value as Failed).exception is SynthesizerException.ProcessingException)

        verify(exactly = 0) {
            mockkPlayer.addMediaItem(any())
            mockkPlayer.prepare()
        }
    }

    @Test
    fun `State updated to failed on error`() {
        tts.speak(TEXT)
        Assert.assertTrue(tts.state.value is Ready)

        utteranceProgressListener.captured.onError(ID)
        Assert.assertTrue(tts.state.value is Failed)
    }

    @Test
    fun `state updates to failed on queue error`() {
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(any(), null, any(), any())
        } returns ERROR

        tts.speak(TEXT)
        Assert.assertTrue(tts.state.value is Failed)
        Assert.assertTrue((tts.state.value as Failed).exception is SynthesizerException.QueueException)
    }

    @Test
    fun `stop resets internal TTS and does player and file cleanup`() {
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)

        val numberOfFiles = 5
        every { mockkPlayer.mediaItemCount } returns numberOfFiles
        every { mockkPlayer.getMediaItemAt(any<Int>()) } returns mockk(relaxed = true)

        tts.stop()
        Assert.assertTrue(tts.state.value is Ready)
        verify(exactly = 1) {
            anyConstructed<TextToSpeech>().shutdown()
            mockkPlayer.stop()
            mockkPlayer.clearMediaItems()
        }
        // Already called once in init
        verify(exactly = 2) {
            anyConstructed<TextToSpeech>().setOnUtteranceProgressListener(any())
        }

        verify(exactly = numberOfFiles) {
            fileUtils.deleteTmpFile(any<MediaItem>())
        }
    }

    @Test
    fun `reset sets playWhenReady if paused`() {
        every { mockkPlayer.isPlaying } returns false
        tts.reset()
        verify { mockkPlayer.playWhenReady = true }
    }

    @Test
    fun `state changes to Finished when player has finished playing`() {
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)
        // State should not change if playback state is not STATE_ENDED
        exoPlayerListener.captured.onIsPlayingChanged(true)
        Assert.assertTrue(tts.state.value is Speaking)

        every { mockkPlayer.playbackState } returns STATE_ENDED
        exoPlayerListener.captured.onIsPlayingChanged(true)
        Assert.assertTrue(tts.state.value is Finished)
    }

    @Test
    fun `state changes to Failed when player hits an error`() {
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)

        val errorMessage = "test error message"
        exoPlayerListener.captured.onPlayerError(
            PlaybackException(
                errorMessage,
                null,
                ERROR_CODE_INVALID_STATE
            )
        )
        Assert.assertTrue(tts.state.value is Failed)
        Assert.assertEquals(
            "Speech playback error: ${errorMessage}.",
            (tts.state.value as Failed).exception.message
        )
    }

    @Test
    fun `pause changes state to paused`() {
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)

        tts.pause()
        Assert.assertTrue(tts.state.value is Paused)
        verify { mockkPlayer.pause() }
    }

    @Test
    fun `pause state remains when queueing new text`() {
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        tts.pause()
        Assert.assertTrue(tts.state.value is Paused)
        verify { mockkPlayer.pause() }

        tts.speak("Another")
        utteranceProgressListener.captured.onStart(ID)
        Assert.assertTrue(tts.state.value is Paused)
        verify { mockkPlayer.pause() }
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Paused)
        verify { mockkPlayer.pause() }
    }

    @Test
    fun `resume changes state to speaking`() {
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)
        // play() is not called here because we use ExpPlayer's 'playWhenReady'.

        tts.pause()
        Assert.assertTrue(tts.state.value is Paused)

        tts.resume()
        Assert.assertTrue(tts.state.value is Speaking)
        verify(exactly = 1) { mockkPlayer.play() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `restart updates player without synthesizing again`() = runTest {
        val stateChanges = mutableListOf<SynthesizerState>()
        val job = launch(UnconfinedTestDispatcher()) {
            tts.state.collect {
                tts.state.toList(stateChanges)
            }
        }

        assertSameStates(listOf(Ready), stateChanges)
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Speaking)
        assertSameStates(listOf(Ready, Processing, Speaking), stateChanges)

        tts.restart()
        Assert.assertTrue(tts.state.value is Speaking)
        assertSameStates(listOf(Ready, Processing, Speaking), stateChanges)
        verify {
            mockkPlayer.seekTo(0, C.TIME_UNSET)
            mockkPlayer.play()
        }

        // Cancel coroutine above because it will never finish on its own.
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `shutdown is unrecoverable`() = runTest {
        val stateChanges = mutableListOf<SynthesizerState>()
        val job = launch(UnconfinedTestDispatcher()) {
            tts.state.collect {
                tts.state.toList(stateChanges)
            }
        }

        tts.shutdown()
        assertSameStates(listOf(Ready, Shutdown()), stateChanges)

        tts.speak(TEXT)
        tts.stop()
        tts.pause()
        tts.resume()
        tts.restart()

        verify(exactly = 0) {
            mockkPlayer.prepare()
            mockkPlayer.play()
            mockkPlayer.pause()
            mockkPlayer.seekTo(any(), any())
        }

        // No actions after shutdown should have an effect.
        assertSameStates(listOf(Ready, Shutdown()), stateChanges)

        // Cancel coroutine above because it will never finish on its own.
        job.cancel()
    }

    @Test
    fun `shutdown stops current speaking and releases player`() {
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        utteranceProgressListener.captured.onDone(ID)
        verify(exactly = 0) {
            mockkPlayer.stop()
            mockkPlayer.release()
        }

        tts.shutdown()
        Assert.assertEquals(Shutdown::class, tts.state.value::class)
        verify(exactly = 1) {
            mockkPlayer.stop()
            anyConstructed<TextToSpeech>().shutdown()
            mockkPlayer.release()
        }
    }

    @Test
    fun `shutdown is called onStop`() {
        Assert.assertFalse(tts.state.value is Shutdown)
        tts.onStop(mockk())

        verify(exactly = 1) {
            tts.shutdown()
            anyConstructed<TextToSpeech>().shutdown()
        }
        Assert.assertTrue(tts.state.value is Shutdown)
    }

    @Test
    fun `state moves to Failed if audio file does not exist`() {
        tts = OSTextToSpeechService(mockkContext, mockkPlayer, TestFileUtils(exists = false))
        tts.speak(TEXT)
        utteranceProgressListener.captured.onStart(ID)
        Assert.assertEquals(Processing, tts.state.value)

        utteranceProgressListener.captured.onDone(ID)
        Assert.assertTrue(tts.state.value is Failed)
        Assert.assertTrue((tts.state.value as Failed).exception is SynthesizerException.ProcessingException)
    }

    @Test
    fun `our isLanguageAvailable matches platform setLanguage return`() {
        val availableLocal = Locale("available")
        val availableCountryLocal = Locale("availableCountry")
        val availableCountryVarLocal = Locale("availableCountryVar")
        val missingLocal = Locale("missingLocale")
        val notSupported = Locale("notSupported")

        every { anyConstructed<TextToSpeech>().isLanguageAvailable(availableLocal) } returns LANG_AVAILABLE
        every { anyConstructed<TextToSpeech>().isLanguageAvailable(availableCountryLocal) } returns LANG_COUNTRY_AVAILABLE
        every { anyConstructed<TextToSpeech>().isLanguageAvailable(availableCountryVarLocal) } returns LANG_COUNTRY_VAR_AVAILABLE
        every { anyConstructed<TextToSpeech>().isLanguageAvailable(missingLocal) } returns LANG_MISSING_DATA
        every { anyConstructed<TextToSpeech>().isLanguageAvailable(notSupported) } returns LANG_NOT_SUPPORTED

        Assert.assertTrue(tts.isLanguageAvailable(availableLocal))
        Assert.assertTrue(tts.isLanguageAvailable(availableCountryLocal))
        Assert.assertTrue(tts.isLanguageAvailable(availableCountryVarLocal))
        Assert.assertFalse(tts.isLanguageAvailable(missingLocal))
        Assert.assertFalse(tts.isLanguageAvailable(notSupported))
    }

    @Test
    fun `setLanguage sets the language`() {
        val availableLocal = Locale("available")
        val localSlot = slot<Locale>()
        every { anyConstructed<TextToSpeech>().isLanguageAvailable(capture(localSlot)) } returns LANG_AVAILABLE

        Assert.assertTrue(tts.setLanguage(availableLocal))
        Assert.assertEquals(availableLocal, localSlot.captured)
    }

    @Test
    fun `setSpeechRate sets rate and returns platform response`() {
        val newSpeechRate = 0.5f
        val speechRateSlot = slot<Float>()
        every { anyConstructed<TextToSpeech>().setSpeechRate(capture(speechRateSlot)) } returns SUCCESS

        Assert.assertTrue(tts.setSpeechRate(newSpeechRate))
        Assert.assertEquals(newSpeechRate, speechRateSlot.captured)

        val failureSpeechRate = 17.0f
        every { anyConstructed<TextToSpeech>().setSpeechRate(failureSpeechRate) } returns ERROR
        Assert.assertFalse(tts.setSpeechRate(failureSpeechRate))
        // It is up to the platform to not set an invalid speech rate so I am not mocking/asserting it.
    }

    @Test
    fun `setPitch sets pitch and returns platform response`() {
        val newPitch = 3.0f
        val pitchSlot = slot<Float>()
        every { anyConstructed<TextToSpeech>().setPitch(capture(pitchSlot)) } returns SUCCESS

        Assert.assertTrue(tts.setPitch(newPitch))
        Assert.assertEquals(newPitch, pitchSlot.captured)

        val failureSpeechRate = -1.0f
        every { anyConstructed<TextToSpeech>().setPitch(failureSpeechRate) } returns ERROR
        Assert.assertFalse(tts.setPitch(failureSpeechRate))
        // It is up to the platform to not set an invalid speech rate so I am not mocking/asserting it.
    }

    private fun assertSameStates(expected: List<SynthesizerState>, actual: List<SynthesizerState>) {
        Assert.assertEquals("Wrong number of states.", expected.count(), actual.count())
        expected.forEachIndexed { index, expectedState ->
            Assert.assertEquals(
                "Wrong state was triggered.",
                expectedState::class,
                actual[index]::class
            )
        }
    }

    private class TestFileUtils(val exists: Boolean = true) :
        OSTextToSpeechService.FileUtils(mockk()) {
        override fun getTmpFile(utteranceId: String?): File {
            return object : File("") {
                override fun exists() = exists
            }
        }

        override fun getFileInfo(utteranceId: String?): Pair<Boolean, String> {
            return exists to ""
        }
    }
}