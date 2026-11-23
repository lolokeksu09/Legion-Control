package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Deterministic worker-queue tests, no external network or user data mutation. */
@RunWith(AndroidJUnit4::class)
class ConnectivityConcurrencyInstrumentedTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()

    private fun emulatorOnly() {
        assumeTrue(Build.FINGERPRINT.lowercase().contains("generic") ||
            Build.FINGERPRINT.lowercase().contains("emulator") ||
            Build.PRODUCT.lowercase().contains("sdk"))
    }

    private fun worker(vm: ConnectivityLabViewModel): ExecutorService {
        val field = ConnectivityLabViewModel::class.java.getDeclaredField("worker")
        field.isAccessible = true
        return field.get(vm) as ExecutorService
    }

    private fun stateOnMain(vm: ConnectivityLabViewModel): ConnectivityLabUiState? {
        val value = AtomicReference<ConnectivityLabUiState?>()
        instrument.runOnMainSync { value.set(vm.state.value) }
        return value.get()
    }

    private fun setup(): Pair<ConnectivityLabViewModel, ViewModelStore> {
        val app = instrument.targetContext.applicationContext as Application
        val vm = ConnectivityLabViewModel(app)
        val store = ViewModelStore()
        instrument.runOnMainSync { store.put("target", vm) }
        return vm to store
    }

    @Test fun cancelBeforeWorkerBeginsReturnsToIdleWithoutNetwork() {
        emulatorOnly()
        val (vm, store) = setup()
        val gate = CountDownLatch(1)
        try {
            val executor = worker(vm)
            val entered = CountDownLatch(1)
            executor.execute {
                entered.countDown()
                gate.await(15, TimeUnit.SECONDS)
            }
            assertTrue("Worker blocker failed", entered.await(5, TimeUnit.SECONDS))
            instrument.runOnMainSync {
                vm.startTest(6)
                vm.cancelTest()
            }
            gate.countDown()
            val deadline = SystemClock.elapsedRealtime() + 6_000
            var current: ConnectivityLabUiState? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                current = stateOnMain(vm)
                if (current != null && !current.testing) break
                Thread.sleep(40)
            }
            assertNotNull(current)
            assertFalse("Cancelled queued test left controls locked", current!!.testing)
            assertTrue(current.message.contains("отмен"))
        } finally {
            gate.countDown()
            instrument.runOnMainSync { store.clear() }
        }
    }

    @Test fun switchingHostInvalidatesQueuedOldResult() {
        emulatorOnly()
        val (vm, store) = setup()
        val gate = CountDownLatch(1)
        try {
            val executor = worker(vm)
            val entered = CountDownLatch(1)
            executor.execute {
                entered.countDown()
                gate.await(15, TimeUnit.SECONDS)
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            instrument.runOnMainSync {
                vm.showHistory() // queued for the original Cloudflare host
                vm.chooseHost("dns.google")
            }
            gate.countDown()
            val barrier = CountDownLatch(1)
            executor.execute { barrier.countDown() }
            assertTrue("Old history worker failed to finish", barrier.await(8, TimeUnit.SECONDS))
            // Flush UI posts. Any stale response must be suppressed by generation check.
            instrument.runOnMainSync { }
            val current = stateOnMain(vm)
            assertEquals("dns.google", vm.host)
            assertNotNull(current)
            assertTrue("Old host's history replaced new host selection",
                current!!.message.startsWith("Выбран dns.google:443"))
        } finally {
            gate.countDown()
            instrument.runOnMainSync { store.clear() }
        }
    }
}
