package com.salesforce.speechsdk.tts

import com.salesforce.speechsdk.tts.SynthesizerException.ProcessingException
import com.salesforce.speechsdk.tts.SynthesizerState.Failed
import com.salesforce.speechsdk.tts.SynthesizerState.Finished
import com.salesforce.speechsdk.tts.SynthesizerState.Paused
import com.salesforce.speechsdk.tts.SynthesizerState.Processing
import com.salesforce.speechsdk.tts.SynthesizerState.Ready
import com.salesforce.speechsdk.tts.SynthesizerState.Shutdown
import com.salesforce.speechsdk.tts.SynthesizerState.Speaking
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class SynthesizerStateMachineTest {
    private lateinit var stateMachine: SynthesizerStateMachine
    private lateinit var state: StateFlow<SynthesizerState>

    @Before
    fun setUp() {
        stateMachine = SynthesizerStateMachine()
        state = stateMachine.asStateFlow()
    }

    @Test
    fun `Starts in Ready state`() {
        Assert.assertTrue(state.value is Ready)
    }

    @Test
    fun `Can transition from Ready to Processing, Failed or Shutdown`() {
        assertTransitions(
            originalState = Ready,
            expectedAllowed = listOf(Processing, Failed(ProcessingException()), Shutdown()),
            expectedNotAllowed = listOf(Ready, Speaking, Paused, Finished),
        )
    }

    @Test
    fun `Can transition from Processing to Speaking, Failed or Shutdown`() {
        assertTransitions(
            originalState = Processing,
            expectedAllowed = listOf(Ready, Speaking, Failed(ProcessingException()), Shutdown()),
            expectedNotAllowed = listOf(Paused, Finished),
        )
    }

    @Test
    fun `Can transition from Speaking to Processing, Paused, Finished, Failed or Shutdown`() {
        assertTransitions(
            originalState = Speaking,
            expectedAllowed = listOf(
                Ready,
                Processing,
                Paused,
                Finished,
                Failed(ProcessingException()),
                Shutdown()
            ),
            expectedNotAllowed = listOf(),
        )
    }

    @Test
    fun `Can transition from Paused to Processing, Speaking, Finished, Failed or Shutdown`() {
        assertTransitions(
            originalState = Paused,
            expectedAllowed = listOf(
                Ready,
                Processing,
                Speaking,
                Finished,
                Failed(ProcessingException()),
                Shutdown()
            ),
            expectedNotAllowed = listOf(),
        )
    }

    @Test
    fun `Can transition from Finished to Processing, Speaking, Finished, Failed or Shutdown`() {
        assertTransitions(
            originalState = Finished,
            expectedAllowed = listOf(
                Ready,
                Processing,
                Speaking,
                Failed(ProcessingException()),
                Shutdown()
            ),
            expectedNotAllowed = listOf(Paused),
        )
    }

    @Test
    fun `Can transition from Failed to Processing or Shutdown`() {
        assertTransitions(
            originalState = Failed(ProcessingException()),
            expectedAllowed = listOf(Ready, Processing, Shutdown()),
            expectedNotAllowed = listOf(),
        )
    }

    @Test
    fun `Can not transition from Shutdown`() {
        assertTransitions(
            originalState = Shutdown(),
            expectedAllowed = listOf(),
            expectedNotAllowed = listOf(
                Ready, Processing, Speaking, Paused,
                Finished, Failed(ProcessingException())
            ),
        )
    }

    @Test
    fun `Only Speaking or Failed states can transition to itself`() {
        val newFailed = Failed(SynthesizerException.InitializationException())
        val newShutdown = Shutdown()

        stateMachine.state.value = Processing
        var result = stateMachine.transitionTo(Processing)
        Assert.assertFalse("Return value is incorrect", result)

        stateMachine.state.value = Speaking
        result = stateMachine.transitionTo(Speaking)
        Assert.assertTrue("Return value is incorrect", result)

        stateMachine.state.value = Paused
        result = stateMachine.transitionTo(Paused)
        Assert.assertFalse("Return value is incorrect", result)

        stateMachine.state.value = Finished
        result = stateMachine.transitionTo(Finished)
        Assert.assertFalse("Return value is incorrect", result)

        stateMachine.state.value = Failed(ProcessingException())
        result = stateMachine.transitionTo(newFailed)
        Assert.assertEquals(newFailed, state.value)
        Assert.assertTrue("Return value is incorrect", result)

        stateMachine.state.value = Shutdown()
        result = stateMachine.transitionTo(newShutdown)
        Assert.assertNotEquals(newShutdown, state.value)
        Assert.assertFalse("Return value is incorrect", result)
    }

    @Test
    fun `transitionTo onSuccess is invoked for allowed transition`() {
        var success = false

        stateMachine.transitionTo(Processing) {
            success = true
        }
        Assert.assertTrue("onSuccess block not invoked.", success)
    }

    @Test
    fun `transitionTo onSuccess is not invoked for illegal transition`() {
        var success = false

        stateMachine.transitionTo(Speaking) {
            success = true
        }
        Assert.assertFalse("onSuccess block should not have been invoked.", success)
    }

    private fun assertTransitions(
        originalState: SynthesizerState,
        expectedAllowed: List<SynthesizerState>,
        expectedNotAllowed: List<SynthesizerState>,
    ) {
        expectedAllowed.forEach { newState ->
            stateMachine.state.value = originalState
            val result = stateMachine.transitionTo(newState)
            Assert.assertTrue(
                "Transition from ${state.value::class} to ${newState::class} should be successful.",
                state.value::class == newState::class,
            )
            Assert.assertTrue("Return value is incorrect", result)
        }

        expectedNotAllowed.forEach { newState ->
            stateMachine.state.value = originalState
            val result = stateMachine.transitionTo(newState)
            // Ready is an object instead, not a class like all other states.
            if (originalState != Ready) {
                Assert.assertFalse(
                    "Transition from ${state.value::class} to ${newState::class} should not be successful.",
                    state.value::class == newState::class,
                )
            }
            Assert.assertFalse("Return value is incorrect", result)
        }
    }
}