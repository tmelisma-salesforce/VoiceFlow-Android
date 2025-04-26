package com.salesforce.speechsdk.tts

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import java.util.Locale

class TextToSpeechManager(
    context: Context,
    model: SynthesizerModel,
) : TextToSpeechService {
    private val textToSpeech = startService(context, model)

    override val state = textToSpeech.state

    override fun speak(utterance: String) = textToSpeech.speak(utterance)

    override fun stop() = textToSpeech.stop()

    override fun pause() = textToSpeech.pause()

    override fun resume() = textToSpeech.resume()

    override fun restart() = textToSpeech.restart()

    override fun isLanguageAvailable(locale: Locale): Boolean =
        textToSpeech.isLanguageAvailable(locale)

    override fun setLanguage(locale: Locale) = textToSpeech.setLanguage(locale)

    override fun setSpeechRate(rate: Float) = textToSpeech.setSpeechRate(rate)

    override fun setPitch(pitch: Float) = textToSpeech.setPitch(pitch)

    override fun shutdown() = textToSpeech.shutdown()

    @OptIn(UnstableApi::class)
    private fun startService(context: Context, model: SynthesizerModel): TextToSpeechService {
        return when (model) {
            SynthesizerModel.OS -> OSTextToSpeechService(context)
        }
    }
}