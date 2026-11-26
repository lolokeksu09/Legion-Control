package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class StartupRecoveryCoordinatorTest {
    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val size: Int get() = tasks.size
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() { tasks.removeFirst().run() }
    }

    @Test fun recoveryIsQueuedAndRunsOnlyOnce() {
        val executor = QueuedExecutor()
        var calls = 0
        val coordinator = StartupRecoveryCoordinator(StartupRecoveryState.Running, executor, { it() }) { calls++ }
        coordinator.start()
        coordinator.start()
        assertEquals(0, calls)
        assertEquals(1, executor.size)
        assertEquals(StartupRecoveryState.Running, coordinator.state)
        executor.runNext()
        assertEquals(1, calls)
        assertEquals(StartupRecoveryState.Ready, coordinator.state)
    }

    @Test fun failureIsVisibleUntilUserAcknowledgesIt() {
        val executor = QueuedExecutor()
        val coordinator = StartupRecoveryCoordinator(StartupRecoveryState.Running, executor, { it() }) {
            throw IllegalStateException("private details are not exposed")
        }
        coordinator.start()
        executor.runNext()
        assertEquals(StartupRecoveryState.Failed("IllegalStateException"), coordinator.state)
        assertTrue(coordinator.continueAfterFailure())
        assertEquals(StartupRecoveryState.ContinuedWithWarning("IllegalStateException"), coordinator.state)
        assertFalse(coordinator.continueAfterFailure())
    }

    @Test fun laterSuccessfulManualRetryClearsWarningForActivityRecreation() {
        val coordinator = StartupRecoveryCoordinator(
            StartupRecoveryState.ContinuedWithWarning("IOException"), QueuedExecutor(), { it() }
        ) {}
        coordinator.markRecovered()
        assertEquals(StartupRecoveryState.Ready, coordinator.state)
    }

    @Test fun cleanInstallDoesNotScheduleRecoveryWork() {
        val executor = QueuedExecutor()
        val coordinator = StartupRecoveryCoordinator(StartupRecoveryState.Ready, executor, { it() }) {
            error("must not run without a journal")
        }
        coordinator.start()
        assertEquals(0, executor.size)
        assertEquals(StartupRecoveryState.Ready, coordinator.state)
    }
}
