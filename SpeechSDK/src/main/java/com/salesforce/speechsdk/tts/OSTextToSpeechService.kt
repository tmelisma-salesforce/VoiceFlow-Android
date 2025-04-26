package com.salesforce.speechsdk.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_AVAILABLE
import android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE
import android.speech.tts.TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.salesforce.speechsdk.tts.SynthesizerState.Failed
import com.salesforce.speechsdk.tts.SynthesizerState.Finished
import com.salesforce.speechsdk.tts.SynthesizerState.Paused
import com.salesforce.speechsdk.tts.SynthesizerState.Processing
import com.salesforce.speechsdk.tts.SynthesizerState.Ready
import com.salesforce.speechsdk.tts.SynthesizerState.Shutdown
import com.salesforce.speechsdk.tts.SynthesizerState.Speaking
import java.io.File
import java.util.Locale

private const val TAG = "OSTextToSpeechService"
private const val AUDIO_FILE_PREFIX = "SpeechSDK"

@UnstableApi
internal class OSTextToSpeechService(
    val context: Context,
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .build(), // only passed in for testing
    private val fileUtils: FileUtils = FileUtils(context),              // only passed in for testing
) : TextToSpeechService, DefaultLifecycleObserver, Player.Listener, UtteranceProgressListener() {
    private val _state = SynthesizerStateMachine()
    override val state = _state.asStateFlow()

    @VisibleForTesting
    internal var textToSpeech = getOSTextToSpeech()

    init {
        // Handle error events and queueing of finished audio files.
        textToSpeech.setOnUtteranceProgressListener(this)

        // Handle error events and set the state to finished when player finishes queue.
        Handler(Looper.getMainLooper()).post {
            player.addListener(this)
            player.playWhenReady = true
        }
    }

    // Region SpeechSDK TextToSpeechService

    override fun speak(utterance: String) {
        utterance.chunked(TextToSpeech.getMaxSpeechInputLength()).forEach { chunk ->
            val id = chunk.hashCode().toString()
            val audioFile = fileUtils.getTmpFile(id)

            // Queue text to be written (as WAV audio) to file.
            val result = textToSpeech.synthesizeToFile(chunk, null, audioFile, id)
            if (result != TextToSpeech.SUCCESS) {
                _state.transitionTo(Failed(SynthesizerException.QueueException()))
                return
            }
        }
    }

    override fun stop() {
        _state.transitionTo(Ready, onSuccess = {
            reset()
        })
    }

    override fun pause() {
        _state.transitionTo(Paused, onSuccess = {
            Handler(Looper.getMainLooper()).post {
                player.pause()
            }
        })
    }

    override fun resume() {
        _state.transitionTo(Speaking, onSuccess = {
            Handler(Looper.getMainLooper()).post {
                player.play()
            }
        })
    }

    override fun restart() {
        _state.transitionTo(Speaking, onSuccess = {
            Handler(Looper.getMainLooper()).post {
                player.seekTo(
                    0, // mediaItemIndex
                    C.TIME_UNSET,
                )
                player.play() // Only needed if we are paused.
            }
        })
    }

    override fun isLanguageAvailable(locale: Locale): Boolean {
        return when (textToSpeech.isLanguageAvailable(locale)) {
            LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE -> true
            else -> false
        }
    }

    override fun setLanguage(locale: Locale): Boolean {
        textToSpeech.language = locale
        return isLanguageAvailable(locale)
    }

    override fun setSpeechRate(rate: Float): Boolean {
        return textToSpeech.setSpeechRate(rate) == TextToSpeech.SUCCESS
    }

    override fun setPitch(pitch: Float): Boolean {
        return textToSpeech.setPitch(pitch) == TextToSpeech.SUCCESS
    }

    override fun shutdown() {
        _state.transitionTo(Shutdown(), onSuccess = {
            reset(restartTts = false)
            Handler(Looper.getMainLooper()).post {
                player.release()
            }
        })
    }

    // endregion

    // Region TextToSpeech UtteranceProgressListener

    override fun onStart(utteranceId: String?) {
        Log.v(TAG, "Text syntheses started for id: $utteranceId.")
        val transitionState = when (state.value) {
            Speaking -> Speaking
            Paused -> Paused
            else -> Processing
        }
        _state.transitionTo(transitionState)
    }

    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) {
        Log.e(TAG, "Text syntheses error for id: $utteranceId.")
        _state.transitionTo(Failed(SynthesizerException.ProcessingException()))
    }

    override fun onDone(utteranceId: String?) {
        val (fileExists, filePath) = fileUtils.getFileInfo(utteranceId)

        if (!fileExists || utteranceId.isNullOrBlank()) {
            _state.transitionTo(Failed(SynthesizerException.ProcessingException()))
            return
        }

        Log.v(TAG, "Text syntheses finished for id: $utteranceId.")
        val mediaItem = MediaItem.Builder()
            .setMediaId(utteranceId)
            .setUri("file://$filePath")
            .build()

        Handler(Looper.getMainLooper()).post {
            // Adds audio file to the end of the playlist.
            player.addMediaItem(mediaItem)

            // Don't transition to speaking if we are paused.
            if (state.value != Paused) {
                _state.transitionTo(Speaking, onSuccess = {
                    with(player) {
                        if (!isPlaying) {
                            when (playbackState) {
                                // This is the first text and have not started speaking.
                                STATE_IDLE -> prepare()
                                // Unused states
                                STATE_BUFFERING, STATE_READY -> { /* Do nothing. */
                                }
                                // The previous file finished playing before this one was sent or finished
                                // processing.  Seek to the file we just added and start playing immediately.
                                STATE_ENDED -> seekTo(mediaItemCount - 1, C.TIME_UNSET)
                            }
                        }
                    }
                })
            }
        }
    }

    // endregion

    // Region Player.Listener

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)

        if (player.playbackState == STATE_ENDED) {
            _state.transitionTo(Finished)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        val exception = SynthesizerException.ProcessingException(
            "Speech playback error: ${error.localizedMessage}.",
            error.cause,
        )
        _state.transitionTo(Failed(exception))
        reset()
    }

    // endregion

    // Region DefaultLifecycleObserver

    // Cleanup when app stops.
    override fun onStop(owner: LifecycleOwner) {
        shutdown()
        super<DefaultLifecycleObserver>.onStop(owner)
    }

    // endregion

    private fun getOSTextToSpeech() = TextToSpeech(context) { status ->
        when (status) {
            TextToSpeech.SUCCESS -> Log.i(TAG, "TTS Service initialized.")
            TextToSpeech.ERROR -> {
                // This should never happen.
                Log.e(TAG, "TTS Service failed to initialize.")
                _state.transitionTo(
                    Shutdown(SynthesizerException.InitializationException())
                )
            }
        }
    }

    @VisibleForTesting
    internal fun reset(restartTts: Boolean = true) {
        // Reset TextToSpeech so anything in-flight doesn't get added to the player
        textToSpeech.shutdown()
        if (restartTts) {
            textToSpeech = getOSTextToSpeech()
            textToSpeech.setOnUtteranceProgressListener(this)
        }

        // Reset Player
        Handler(Looper.getMainLooper()).post {
            if (player.isPlaying) {
                player.stop()
            } else {
                // This needs to be set again if we were paused.
                player.playWhenReady = true
            }

            // Delete all audio files
            val count = player.mediaItemCount
            for (index in 0..<count) {
                fileUtils.deleteTmpFile(player.getMediaItemAt(index))
            }

            player.clearMediaItems()
        }
    }

    // This abstraction looks really silly, but there is a longstanding bug in mockk that
    // prevents mocking File.  This even extends to getCacheDir in Context, which returns File.
    @VisibleForTesting
    internal open class FileUtils(val context: Context) {

        open fun getTmpFile(utteranceId: String?) =
            File("${context.cacheDir}/$AUDIO_FILE_PREFIX-$utteranceId")
                .also { it.deleteOnExit() }

        open fun deleteTmpFile(mediaItem: MediaItem) {
            val file = getTmpFile(mediaItem.mediaId)
            if (file.exists()) {
                file.delete()
            }
        }

        open fun getFileInfo(utteranceId: String?): Pair<Boolean, String> {
            with(getTmpFile(utteranceId)) {
                return exists() to path
            }
        }
    }
}