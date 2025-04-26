package com.salesforce.speechsdk.tts

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Enum representation of the available synthesizer engines.
 */
enum class SynthesizerModel {
    OS,
}

/**
 * Representation of the current state of the synthesizer service.
 */
sealed class SynthesizerState {
    /**
     * Initial [StateFlow] state.  Will return to this state when [TextToSpeechManager.stop] is called
     * to indicate [TextToSpeechManager.speak] will begin a new speaking queue.
     */
    data object Ready : SynthesizerState()

    /**
     * State indicating the service is working on speech synthesis but speaking has not begun.
     */
    data object Processing : SynthesizerState()

    /**
     * State indicating the service is currently speaking.
     */
    data object Speaking : SynthesizerState()

    /**
     * State indicating the service has paused speaking.
     */
    data object Paused : SynthesizerState()

    /**
     * State indicating the service has finished speaking.
     */
    data object Finished : SynthesizerState()

    /**
     * State indicating an error has occurred.
     *
     * @param exception the [SynthesizerException] that caused the failure state.
     */
    class Failed(val exception: SynthesizerException) : SynthesizerState()

    /**
     * State indicating the service has been shutdown and unloaded.  Re-instantiate the manger to
     * user the service again.
     *
     * @param exception the cause, if the shutdown was triggered by an error.
     */
    class Shutdown(val exception: SynthesizerException? = null) : SynthesizerState()
}

/**
 * Possible synthesizer exceptions.
 *
 * @param message the detailed error message
 * @param cause the cause of this throwable
 */
sealed class SynthesizerException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Throwable() {
    /**
     * Exception indicating the synthesizer model could not be started.
     */
    class InitializationException : SynthesizerException()

    /**
     * Exception indicating the synthesizer model was unable to queue the work for the requested text.
     */
    class QueueException : SynthesizerException()

    /**
     * Exception indicating the synthesizer model encountered an error while processing or speaking.
     */
    class ProcessingException(message: String? = null, cause: Throwable? = null) :
        SynthesizerException(message, cause)
}

interface TextToSpeechService {
    /**
     * The state of the service. See [SynthesizerState].
     *
     * Use the `is` keyword to compare states and [StateFlow.collect] to delegate on state change.
     */
    val state: StateFlow<SynthesizerState>

    /**
     * Begins text to speech synthesis and speaking.  You may call this function repeatedly to queue text.
     *
     * @param utterance the text to speak.
     */
    fun speak(utterance: String)

    /**
     * Stops text to speech audio, cancels any processing in progress and clears everything processed.
     */
    fun stop()

    /**
     * Pauses text to speech audio, if possible.
     */
    fun pause()

    /**
     * Resumes text to speech audio, if possible.
     */
    fun resume()

    /**
     * Stops text to speech audio and begins speaking from the beginning of the provided text.
     */
    fun restart()

    /**
     *  Checks if the language specified by the locale parameter is available.
     *
     *  @param locale the desired locale.
     *  @return true if a close language match is available.
     */
    fun isLanguageAvailable(locale: Locale): Boolean

    /**
     *  Attempts to set the requested locale.  The closest available option may be used.
     *
     *  @param locale the desired locale.
     *  @return true if a close language match is available.
     */
    fun setLanguage(locale: Locale): Boolean

    /**
     * Changes the output to a specific speech rate.
     * 1.0 is the default.
     * 0.5 is half speed.
     * 2.0 is double speed.
     *
     * @param rate the desired speech rate.
     * @return true if successful
     */
    fun setSpeechRate(rate: Float): Boolean

    /**
     * Sets the pitch of the output.
     * 1.0 if the default.
     * Lower input will create a lower tone, higher input will create a higher tone.
     *
     * @param pitch the desired
     * @return true if successful.
     */
    fun setPitch(pitch: Float): Boolean

    /**
     * Unloads the model from memory and sets the state to [SynthesizerState.Shutdown].  Re-instantiate
     * the manager to use the service again.
     */
    fun shutdown()
}