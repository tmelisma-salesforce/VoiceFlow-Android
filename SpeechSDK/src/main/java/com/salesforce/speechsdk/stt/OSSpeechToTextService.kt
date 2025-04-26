package com.salesforce.speechsdk.stt

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val TAG = "PlatformFallbackSpeechToTextService"

internal class OSSpeechToTextService(
    context: Context,
    private val permissionRationaleText: String? = null
) : SpeechToTextService {
    private val _state = TranscriptionStateMachine()
    override val state = _state.asStateFlow()

    private var _text = MutableLiveData("")
    override val text: LiveData<String> = _text

    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionListener: LongTranscriptionRecognitionListener? = null

    @VisibleForTesting
    internal var languageTag = Locale.getDefault().toLanguageTag()

    @VisibleForTesting
    internal var silenceLength = 30.seconds

    override fun setLanguage(languageTag: String) {
        this.languageTag = languageTag
    }

    override fun setSilenceLength(silenceLength: Duration) {
        this.silenceLength = silenceLength
    }

    override fun startTranscribing() {
        // Session is already in progress so can't start another
        if (speechRecognizer != null) {
            Log.e(TAG, "Cannot start transcribing when another session is in progress.")
            processFailure(TranscriptionException.sessionInProgress(appContext))
            return
        }

        checkPerms(
            permissionRationaleText,
            onSuccessBlock = { doStartTranscribing() },
            onFailureBlock = { failure -> processFailure(failure) }
        )
    }

    override fun stopTranscribing() {
        doStopTranscribing()
    }

    private fun processResult(result: String) {
        _state.transitionTo(TranscriptionState.Processing, onSuccess = {
            _text.value = result
        })
    }

    private fun processFailure(failure: TranscriptionException) {
        Log.e(TAG, "Transcribing Failure - ${failure.message}")

        // when a failure occurs we stop the speech recognition session
        doStopTranscribing(failure)
    }

    private fun checkPerms(
        permissionRationaleText: String?,
        onSuccessBlock: () -> Unit,
        onFailureBlock: (TranscriptionException) -> Unit
    ) {
        PermissionActivity.requestMicrophonePermission(
            appContext,
            permissionRationaleText
        ) { granted ->
            if (granted) {
                onSuccessBlock.invoke()
            } else {
                onFailureBlock.invoke(
                    TranscriptionException.userDeniedPermissionToMicrophone(
                        appContext
                    )
                )
            }
        }
    }

    private fun releaseSpeechRecognizer() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        recognitionListener?.close()
        recognitionListener = null
    }

    private fun doStartTranscribing() {
        Handler(Looper.getMainLooper()).post {
            // First check to see whether speech recognition is supported at all
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                processFailure(TranscriptionException.recognitionNotSupported(appContext))
                return@post
            }

            // Next check to see if on-device speech recognition is supported.
            if (isOnDeviceRecognitionAvailable()) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                processFailure(TranscriptionException.onDeviceRecognitionNotSupported(appContext))
                return@post
            }

            val listener = LongTranscriptionRecognitionListener(
                languageTag,
                silenceLength,
                updateResults = { processResult(it ?: "") },
                endSpeech = { failure ->
                    if (failure != null) {
                        processFailure(failure)
                    } else {
                        // automatically terminate transcribing session when long silence detected
                        doStopTranscribing()
                    }
                }
            )

            _state.transitionTo(TranscriptionState.Ready, onSuccess = {
                speechRecognizer?.setRecognitionListener(listener)
                speechRecognizer?.startListening(listener.speechIntent())
                recognitionListener = listener
            })
        }
    }

    private fun doStopTranscribing(failure: TranscriptionException? = null) {
        Handler(Looper.getMainLooper()).post {
            if (failure != null) {
                _state.transitionTo(TranscriptionState.Failed(failure))
            } else {
                _state.transitionTo(TranscriptionState.Finished)
            }
            releaseSpeechRecognizer()
        }
    }

    @VisibleForTesting
    internal fun isOnDeviceRecognitionAvailable(): Boolean {
        // TODO:  Remove this check when min API > 30
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(
            appContext
        )
    }
}
