package com.salesforce.speechsdk.stt

import android.util.Log
import com.salesforce.speechsdk.stt.TranscriptionState.Failed
import com.salesforce.speechsdk.stt.TranscriptionState.Finished
import com.salesforce.speechsdk.stt.TranscriptionState.Initialized
import com.salesforce.speechsdk.stt.TranscriptionState.Processing
import com.salesforce.speechsdk.stt.TranscriptionState.Ready
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State Machine for [TranscriptionState].
 */
internal class TranscriptionStateMachine {
    private val state = MutableStateFlow<TranscriptionState>(Initialized)

    /**
     * Passthrough for [MutableStateFlow.asStateFlow].
     *
     * @return [StateFlow] of the machine.
     */
    fun asStateFlow(): StateFlow<TranscriptionState> {
        return state.asStateFlow()
    }

    /**
     * Update function that follows a finite state machine model. Emits a the value for observers only
     * if the state change is allowed.
     *
     * @param newState the state to attempt to update to
     * @param onSuccess optional block to execute if transition to new state is successful.
     * @return true if the state was updated
     */
    fun transitionTo(newState: TranscriptionState, onSuccess: (() -> Unit)? = null): Boolean {
        val transitionSuccess = when (state.value) {
            is Initialized -> {
                when (newState) {
                    is Ready, is Failed -> state.tryEmit(newState)
                    else -> false
                }
            }

            is Ready, is Processing -> {
                when (newState) {
                    is Processing, is Finished, is Failed -> state.tryEmit(newState)
                    else -> false
                }
            }

            is Finished -> {
                when (newState) {
                    is Ready, is Failed -> state.tryEmit(newState)
                    else -> false
                }
            }

            is Failed -> {
                when (newState) {
                    is Ready, is Failed -> state.tryEmit(newState)
                    else -> false
                }
            }
        }

        if (transitionSuccess) {
            onSuccess?.invoke()
        } else {
            Log.e(TAG, "Unable to transition from ${state.value} to $newState.")
        }

        return transitionSuccess
    }

    companion object {
        const val TAG = "TranscriptionStateMachine"
    }
}
