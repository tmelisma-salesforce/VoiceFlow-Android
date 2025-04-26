package com.salesforce.speechsdk.tts

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.salesforce.speechsdk.tts.SynthesizerState.Failed
import com.salesforce.speechsdk.tts.SynthesizerState.Finished
import com.salesforce.speechsdk.tts.SynthesizerState.Paused
import com.salesforce.speechsdk.tts.SynthesizerState.Processing
import com.salesforce.speechsdk.tts.SynthesizerState.Ready
import com.salesforce.speechsdk.tts.SynthesizerState.Shutdown
import com.salesforce.speechsdk.tts.SynthesizerState.Speaking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State Machine for [SynthesizerState].
 */
internal class SynthesizerStateMachine {
    @VisibleForTesting
    internal val state = MutableStateFlow<SynthesizerState>(Ready)

    /**
     * Passthrough for [MutableStateFlow.asStateFlow].
     *
     * @return [StateFlow] of the machine.
     */
    fun asStateFlow(): StateFlow<SynthesizerState> {
        return state.asStateFlow()
    }

    /**
     * Update function that follows a finite state machine model. Emits a the value for observers only
     * if the state change is allowed.
     *
     * @param newState the state to attempt to update to.
     * @param onSuccess optional block to execute if transition to new state is successful.
     * @return true if the state was updated.
     */
    fun transitionTo(newState: SynthesizerState, onSuccess: (() -> Unit)? = null): Boolean {
        val transitionSuccess = when (state.value) {
            is Ready -> {
                when (newState) {
                    Processing, is Failed, is Shutdown -> state.tryEmit(newState)
                    else -> false
                }
            }

            is Processing -> {
                when (newState) {
                    Ready, Speaking, is Failed, is Shutdown -> state.tryEmit(newState)
                    else -> false
                }
            }

            is Speaking -> {
                when (newState) {
                    Ready, Processing, Speaking, Paused, Finished, is Failed, is Shutdown -> state.tryEmit(
                        newState
                    )
                }
            }

            is Paused -> {
                when (newState) {
                    Ready, Processing, Speaking, Finished, is Failed, is Shutdown -> state.tryEmit(
                        newState
                    )

                    else -> false
                }
            }

            is Finished -> {
                when (newState) {
                    Ready, Processing, Speaking, is Failed, is Shutdown -> state.tryEmit(newState)
                    else -> false
                }
            }

            is Failed -> {
                when (newState) {
                    Ready, Processing, is Failed, is Shutdown -> state.tryEmit(newState)
                    else -> false
                }
            }

            is Shutdown -> false
        }

        if (transitionSuccess) {
            onSuccess?.invoke()
        } else {
            Log.e(TAG, "Unable to transition from ${state.value} to $newState.")
        }

        return transitionSuccess
    }

    companion object {
        const val TAG = "SynthesizerStateMachine"
    }
}