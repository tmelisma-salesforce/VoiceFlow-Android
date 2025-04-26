package com.salesforce.speechsdk.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Test
import java.io.File
import java.util.Locale

class TextToSpeechManagerTest {
    val locale = Locale("it-IT")

    @Test
    fun `Initializing with PLATFORM_FALLBACK uses platform fallback option`() {
        mockkConstructor(OSTextToSpeechService::class)
        every { anyConstructed<OSTextToSpeechService>().isLanguageAvailable(any()) } returns true
        every { anyConstructed<OSTextToSpeechService>().setLanguage(any()) } returns true
        every { anyConstructed<OSTextToSpeechService>().setSpeechRate(any()) } returns true
        every { anyConstructed<OSTextToSpeechService>().setPitch(any()) } returns true
        mockkConstructor(TextToSpeech::class)
        every {
            anyConstructed<TextToSpeech>().synthesizeToFile(
                any(),
                any(),
                any<File>(),
                any()
            )
        } returns TextToSpeech.SUCCESS
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns mockk()
        mockkStatic(TextToSpeech::class)
        every { TextToSpeech.getMaxSpeechInputLength() } returns MAX_LENGTH

        val mockkContext = mockk<Context>(relaxed = true)
        every { mockkContext.cacheDir } returns File("/test")
        val manager = TextToSpeechManager(mockkContext, SynthesizerModel.OS)
        val ttsText = "OS provided tts"

        // Set language, speech rate and pitch.
        manager.isLanguageAvailable(locale)
        verify { anyConstructed<OSTextToSpeechService>().isLanguageAvailable(locale) }
        manager.setLanguage(locale)
        verify { anyConstructed<OSTextToSpeechService>().setLanguage(locale) }
        manager.setSpeechRate(0.5f)
        verify { anyConstructed<OSTextToSpeechService>().setSpeechRate(any()) }
        manager.setPitch(0.5f)
        verify { anyConstructed<OSTextToSpeechService>().setPitch(any()) }

        // Speaking
        manager.speak(ttsText)
        verify {
            anyConstructed<OSTextToSpeechService>().speak(ttsText)
            anyConstructed<TextToSpeech>().synthesizeToFile(
                ttsText,
                null,
                any<File>(),
                ttsText.hashCode().toString()
            )
        }
        manager.stop()
        verify { anyConstructed<OSTextToSpeechService>().stop() }

        // Media Controls
        manager.pause()
        verify { anyConstructed<OSTextToSpeechService>().pause() }
        manager.resume()
        verify { anyConstructed<OSTextToSpeechService>().resume() }
        manager.restart()
        verify { anyConstructed<OSTextToSpeechService>().restart() }

        // Shutdown
        manager.shutdown()
        verify { anyConstructed<OSTextToSpeechService>().shutdown() }
    }
}

