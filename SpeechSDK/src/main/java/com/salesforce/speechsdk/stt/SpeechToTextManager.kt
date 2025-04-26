package com.salesforce.speechsdk.stt

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlin.time.Duration

class SpeechToTextManager(
    context: Context,
    model: TranscriptionModel,
    permissionRationaleText: String? = null
) : SpeechToTextService {
    @VisibleForTesting
    internal val speechToText = startService(context, model, permissionRationaleText)

    override val state = speechToText.state

    override val text = speechToText.text

    override fun setLanguage(languageTag: String) = speechToText.setLanguage(languageTag)

    override fun setSilenceLength(silenceLength: Duration) =
        speechToText.setSilenceLength(silenceLength)

    override fun startTranscribing() = speechToText.startTranscribing()

    override fun stopTranscribing() = speechToText.stopTranscribing()

    private fun startService(
        context: Context,
        model: TranscriptionModel,
        permissionRationaleText: String?
    ): SpeechToTextService {
        return when (model) {
            TranscriptionModel.OS -> OSSpeechToTextService(context, permissionRationaleText)
        }
    }
}