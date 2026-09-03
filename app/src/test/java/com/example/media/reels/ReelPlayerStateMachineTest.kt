package com.example.media.reels

import com.example.core.media.reels.ReelPlaybackState

  import com.example.core.media.reels.ReelPlayerStateMachine
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class ReelPlayerStateMachineTest {

      @Test
      fun initialStateIsBufferingWithoutError() {
          val machine = ReelPlayerStateMachine()
          assertTrue(machine.state.value.isBuffering)
          assertFalse(machine.state.value.hasError)
          assertFalse(machine.state.value.isReady)
          assertEquals(0, machine.state.value.retriesUsed)
      }

      @Test
      fun bufferingThenReadyClearsBuffering() {
          val machine = ReelPlayerStateMachine()
          machine.onPlaybackStateChanged(ReelPlaybackState.BUFFERING)
          machine.onPlaybackStateChanged(ReelPlaybackState.READY)
          assertFalse(machine.state.value.isBuffering)
          assertTrue(machine.state.value.isReady)
          assertFalse(machine.state.value.hasError)
      }

      @Test
      fun singleErrorIsAbsorbedByRetry() {
          val machine = ReelPlayerStateMachine()
          machine.onPlaybackStateChanged(ReelPlaybackState.READY)
          val absorbed = machine.onPlayerError()
          assertTrue(absorbed)
          assertFalse(machine.state.value.hasError)
          assertTrue(machine.state.value.isBuffering)
          assertEquals(1, machine.state.value.retriesUsed)
      }

      @Test
      fun threeErrorsFlagVisualError() {
          val machine = ReelPlayerStateMachine()
          machine.onPlayerError()
          machine.onPlayerError()
          val third = machine.onPlayerError()
          assertFalse(third)
          assertTrue(machine.state.value.hasError)
          assertFalse(machine.state.value.isBuffering)
          assertEquals(2, machine.state.value.retriesUsed)
      }

      @Test
      fun explicitRetryResetRestoresBuffering() {
          val machine = ReelPlayerStateMachine()
          machine.onPlaybackStateChanged(ReelPlaybackState.READY)
          machine.onPlayerError()
          machine.onPlayerError()
          machine.onPlayerError()
          assertTrue(machine.state.value.hasError)
          machine.onRetryReset()
          assertFalse(machine.state.value.hasError)
          assertTrue(machine.state.value.isBuffering)
          assertEquals(0, machine.state.value.retriesUsed)
      }

      @Test
      fun readyAfterRetryAbsorbsFailure() {
          val machine = ReelPlayerStateMachine()
          machine.onPlayerError()
          machine.onPlaybackStateChanged(ReelPlaybackState.READY)
          val absorbed = machine.onPlayerError()
          assertTrue(absorbed)
          assertEquals(1, machine.state.value.retriesUsed)
      }
  }