package dev.journeyscope.export

import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.export.persistence.JourneyPersistence
import dev.journeyscope.export.persistence.PersistingJourneyRecorder
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.storage.StorageEvent
import dev.journeyscope.export.storage.StorageListener
import dev.journeyscope.export.storage.TraceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PersistenceTest {
    private val config = JourneyScopeConfig(enabled = true)

    @Test
    fun disabledSdkOrDisabledPolicyTouchesNoStorage() =
        runTest {
            var created = 0
            val factory = {
                created++
                FakeFileSystem()
            }
            val sdkOff =
                JourneyPersistence.install(
                    JourneyScopeConfig(enabled = false),
                    ExportPolicy(persistenceEnabled = true),
                    fileSystem = factory,
                )
            val policyOff = JourneyPersistence.install(config, ExportPolicy(persistenceEnabled = false), fileSystem = factory)
            assertEquals(0, created)
            assertFalse(sdkOff.isPersisting)
            assertFalse(policyOff.isPersisting)
            val tracer = JourneyTracer(JourneyScopeConfig(enabled = false), sdkOff.recorder)
            tracer.startJourney("x").finish()
            assertTrue(sdkOff.recorder.snapshot().isEmpty())
        }

    @Test
    fun finishedJourneysArePersistedOffTheCallerPath() =
        runTest {
            val fs = FakeFileSystem()
            val persistence =
                JourneyPersistence.install(config, ExportPolicy(persistenceEnabled = true), fileSystem = {
                    fs
                }, isEnabled = { true }, clock = FakeClock())
            val tracer = JourneyTracer(config, persistence.recorder, FakeClock(), CountingIds("p"))
            tracer.startJourney("one").finish()
            tracer.startJourney("two").finish()
            persistence.flush()
            assertEquals(2, fs.files.size)
            assertEquals(2, persistence.store?.loadAll()?.size)
            persistence.close()
        }

    @Test
    fun interruptedJourneysAreSavedAsInterrupted() =
        runTest {
            val fs = FakeFileSystem()
            val persistence =
                JourneyPersistence.install(config, ExportPolicy(persistenceEnabled = true), fileSystem = {
                    fs
                }, isEnabled = { true }, clock = FakeClock())
            val tracer = JourneyTracer(config, persistence.recorder, FakeClock(), CountingIds("i"))
            tracer.startJourney("running")
            tracer.interruptActive("backgrounded")
            persistence.flush()
            assertEquals(
                TraceStatus.INTERRUPTED,
                persistence.store
                    ?.loadAll()
                    ?.single()
                    ?.status,
            )
            persistence.close()
        }

    @Test
    fun runningJourneysAreNeverWrittenPartially() =
        runTest {
            val fs = FakeFileSystem()
            val persistence =
                JourneyPersistence.install(config, ExportPolicy(persistenceEnabled = true), fileSystem = {
                    fs
                }, isEnabled = { true }, clock = FakeClock())
            val tracer = JourneyTracer(config, persistence.recorder, FakeClock(), CountingIds("r"))
            val journey = tracer.startJourney("in-flight")
            journey.recordEvent("tick")
            persistence.flush()
            assertTrue(fs.files.isEmpty())
            journey.finish()
            persistence.close()
        }

    @Test
    fun disablingTheSdkStopsPersistingWithoutClosing() =
        runTest {
            val fs = FakeFileSystem()
            var enabled = true
            val store = TraceStore(fs, ExportPolicy(persistenceEnabled = true), FakeClock(), StorageListener.None, Dispatchers.Unconfined)
            val recorder =
                PersistingJourneyRecorder(InMemoryJourneyRecorder(), store, 4, isEnabled = { enabled }, dispatcher = Dispatchers.Unconfined)
            val tracer = JourneyTracer(config, recorder, FakeClock(), CountingIds("d"))
            enabled = false
            tracer.startJourney("ignored").finish()
            recorder.flush()
            assertTrue(fs.files.isEmpty())
            recorder.close()
        }

    @Test
    fun fullQueueDropsOldestWithoutBlockingWriters() =
        runTest {
            val events = ArrayList<StorageEvent>()
            val fs = FakeFileSystem()
            val store = TraceStore(fs, ExportPolicy(persistenceEnabled = true), FakeClock(), StorageListener.None, Dispatchers.Unconfined)
            val recorder =
                PersistingJourneyRecorder(
                    InMemoryJourneyRecorder(),
                    store,
                    2,
                    StorageListener { events += it },
                    dispatcher = StandardTestDispatcher(testScheduler),
                    isEnabled = { true },
                )
            val tracer = JourneyTracer(config, recorder, FakeClock(), CountingIds("q"))
            repeat(20) { tracer.startJourney("j$it").finish() }
            assertEquals(18, events.count { it is StorageEvent.QueueOverflow })
            advanceUntilIdle()
            assertEquals(2, fs.files.size)
            recorder.close()
        }
}
