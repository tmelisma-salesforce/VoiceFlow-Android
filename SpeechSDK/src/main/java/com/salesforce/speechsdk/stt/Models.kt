package com.salesforce.speechsdk.stt

import android.content.Context
import androidx.lifecycle.LiveData
import com.salesforce.speechsdk.R
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Enum representation of the available speech recognition engines.
 */
enum class TranscriptionModel {
    OS,
}

/**
 * Representation of the current state of the speech recognition service.
 */
sealed class TranscriptionState {
    data object Initialized : TranscriptionState()
    data object Ready : TranscriptionState()
    data object Processing : TranscriptionState()
    data object Finished : TranscriptionState()
    class Failed(val exception: TranscriptionException) : TranscriptionState()
}

/**
 * Possible speech recognizer exceptions.
 *
 * @param message the detailed error message
 * @param cause the cause of this throwable
 */
sealed class TranscriptionException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Throwable() {
    class PermissionException(message: String, throwable: Throwable? = null) :
        TranscriptionException(message, throwable)

    class SessionException(message: String, throwable: Throwable? = null) :
        TranscriptionException(message, throwable)

    class RecognitionNotSupportedException(message: String, throwable: Throwable? = null) :
        TranscriptionException(message, throwable)

    class OnDeviceRecognitionNotSupportedException(message: String, throwable: Throwable? = null) :
        TranscriptionException(message, throwable)

    class GenericException(message: String, throwable: Throwable? = null) :
        TranscriptionException(message, throwable)

    companion object {
        fun userDeniedPermissionToMicrophone(context: Context): TranscriptionException {
            return PermissionException(
                context.resources.getString(R.string.speechsdk_user_denied_permission_to_microphone)
            )
        }

        fun sessionInProgress(context: Context): TranscriptionException {
            return SessionException(
                context.resources.getString(R.string.speechsdk_session_in_progress)
            )
        }

        fun recognitionNotSupported(context: Context): TranscriptionException {
            return RecognitionNotSupportedException(
                context.resources.getString(R.string.speechsdk_recognition_not_supported)
            )
        }

        fun onDeviceRecognitionNotSupported(context: Context): TranscriptionException {
            return OnDeviceRecognitionNotSupportedException(
                context.resources.getString(R.string.speechsdk_on_device_recognition_not_supported)
            )
        }

        fun genericError(message: String, throwable: Throwable? = null): TranscriptionException {
            return GenericException(message, throwable)
        }
    }
}

interface SpeechToTextService {
    /**
     * The state of the service. See [TranscriptionState].
     *
     * Use the `is` keyword to compare states and [StateFlow.collect] to delegate on state change.
     */
    val state: StateFlow<TranscriptionState>

    /**
     * The detected text as recognized by the speech recognizer.
     */
    val text: LiveData<String>

    /**
     *  Sets the requested language for the speech recognizer.
     *
     *  @param languageTag the IETF language tag (as defined by BCP 47), for example "en-US"
     */
    fun setLanguage(languageTag: String)

    /**
     *  Sets the amount of time to wait when silence is detected before automatically ending
     *  the speech recognition session.  The default length is 30 seconds.
     *
     *  @param silenceLength the duration to wait
     */
    fun setSilenceLength(silenceLength: Duration)

    /**
     * Begins speech recognition session.
     */
    fun startTranscribing()

    /**
     * Ends speech recognition session.
     */
    fun stopTranscribing()
}