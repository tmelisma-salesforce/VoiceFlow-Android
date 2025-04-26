package com.salesforce.speechsdk.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionStateMachineTest {
    @Test
    fun `defaults to Initialized state`() {
        val stateMachine = TranscriptionStateMachine()
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Initialized)
    }

    @Test
    fun `does not transition and execute when not allowed`() {
        // Test => Initialized to Finished => not allowed
        val stateMachine = TranscriptionStateMachine()
        var executed = false
        assertFalse(
            stateMachine.transitionTo(
                TranscriptionState.Finished,
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Initialized) // remains Ready
        assertFalse(executed)
    }

    @Test
    fun `transitions and executes when allowed`() {
        // Test => Initialized to Ready
        var stateMachine = TranscriptionStateMachine()
        var executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Ready,
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Ready)
        assertTrue(executed)

        // Test => Initialized to Failed
        stateMachine = TranscriptionStateMachine()
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Failed(TranscriptionException.genericError("test error")),
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Failed)
        assertTrue(executed)

        // Test => Ready to Processing
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Ready)
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Processing,
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Processing)
        assertTrue(executed)

        // Test => Ready to Finished
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Ready)
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Finished,
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Finished)
        assertTrue(executed)

        // Test => Ready to Failed
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Ready)
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Failed(TranscriptionException.genericError("test error")),
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Failed)
        assertTrue(executed)

        // Test => Processing to Finished
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Ready)
        stateMachine.transitionTo(TranscriptionState.Processing)
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Finished,
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Finished)
        assertTrue(executed)

        // Test => Processing to Failed
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Ready)
        stateMachine.transitionTo(TranscriptionState.Processing)
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Failed(TranscriptionException.genericError("test error")),
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Failed)
        assertTrue(executed)

        // Test => Finished to Ready
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Ready)
        stateMachine.transitionTo(TranscriptionState.Processing)
        stateMachine.transitionTo(TranscriptionState.Finished)
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Ready,
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Ready)
        assertTrue(executed)

        // Test => Finished to Failed
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Ready)
        stateMachine.transitionTo(TranscriptionState.Processing)
        stateMachine.transitionTo(TranscriptionState.Finished)
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Failed(TranscriptionException.genericError("test error")),
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Failed)
        assertTrue(executed)

        // Test => Failed to Ready
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Failed(TranscriptionException.genericError("test error")))
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Ready,
                onSuccess = { executed = true }
            )
        )
        assertTrue(stateMachine.asStateFlow().value is TranscriptionState.Ready)
        assertTrue(executed)

        // Test => Failed to Failed
        stateMachine = TranscriptionStateMachine()
        stateMachine.transitionTo(TranscriptionState.Failed(TranscriptionException.genericError("test error 1")))
        executed = false
        assertTrue(
            stateMachine.transitionTo(
                TranscriptionState.Failed(TranscriptionException.genericError("test error 2")),
                onSuccess = { executed = true }
            )
        )
        val failure = stateMachine.asStateFlow().value as? TranscriptionState.Failed
        assertEquals("test error 2", failure?.exception?.message)
        assertTrue(executed)
    }
}