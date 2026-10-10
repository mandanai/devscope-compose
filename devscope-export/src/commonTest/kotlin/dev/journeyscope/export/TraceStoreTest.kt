package dev.journeyscope.export

import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.storage.SaveOutcome
import dev.journeyscope.export.storage.StorageEvent
import dev.journeyscope.export.storage.StorageListener
import dev.journeyscope.export.storage.TraceQuery
import dev.journeyscope.export.storage.TraceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class TraceStoreTest {
    private val fs = FakeFileSystem()
    private val events = ArrayList<StorageEvent>()
    private val clock = FakeClock()

    private fun store(policy: ExportPolicy = ExportPolicy(persistenceEnabled = true), fileSystem: FakeFileSystem = fs) =
        TraceStore(fileSystem, policy, clock, StorageListener { events += it }, Dispatchers.Unconfined)

    private fun trace(id: String, wall: Long = clock.wall) =
        sampleTrace(FakeClock(wall = wall), ids = id).let { it.copy(id = TraceId("t-$id")) }

    @Test
    fun saveThenLoadRoundTrips() = runTest {
        val store = store()
        val original = trace("a")
        assertEquals(SaveOutcome.SAVED, store.saveTrace(original))
        val loaded = assertNotNull(store.load(original.id))
        assertEquals(original.id, loaded.id)
        assertEquals(original.spans.size, loaded.spans.size)
        assertEquals(original.status, loaded.status)
        assertEquals(1, store.loadAll().size)
    }

    @Test
    fun neverStoresSensitiveValues() = runTest {
        store().saveTrace(trace("a"))
        assertTrue(fs.files.values.none { it.contains("abcdefghijklmnop") || it.contains("authorization") })
    }

    @Test
    fun retentionDropsOldTracesOnNextSave() = runTest {
        val store = store(ExportPolicy(persistenceEnabled = true, retention = 1.days))
        store.saveTrace(trace("old", wall = clock.wall - 2.days.inWholeMilliseconds))
        store.saveTrace(trace("new"))
        assertEquals(listOf(TraceId("t-new")), store.loadAll().map { it.id })
    }

    @Test
    fun countAndByteQuotasEvictOldestFirst() = runTest {
        val byCount = store(ExportPolicy(persistenceEnabled = true, maxStoredTraces = 2))
        listOf("a", "b", "c").forEachIndexed { i, id -> byCount.saveTrace(trace(id, wall = clock.wall + i)) }
        assertEquals(listOf("t-b", "t-c"), byCount.loadAll().map { it.id.value })

        val smallFs = FakeFileSystem()
        val oneSize = run {
            val probe = store(fileSystem = FakeFileSystem())
            probe.saveTrace(trace("p"))
            probe.storedBytes()
        }
        val byBytes = store(ExportPolicy(persistenceEnabled = true, maxTotalBytes = oneSize * 2 + 10, maxTraceBytes = oneSize * 2), smallFs)
        listOf("a", "b", "c", "d").forEachIndexed { i, id -> byBytes.saveTrace(trace(id, wall = clock.wall + i)) }
        assertTrue(byBytes.storedBytes() <= oneSize * 2 + 10)
        assertTrue(byBytes.loadAll().last().id.value == "t-d")
    }

    @Test
    fun oversizedTracesAreRejectedNotTruncated() = runTest {
        val store = store(ExportPolicy(persistenceEnabled = true, maxTotalBytes = 600, maxTraceBytes = 500))
        assertEquals(SaveOutcome.TOO_LARGE, store.saveTrace(trace("a")))
        assertTrue(fs.files.isEmpty())
    }

    @Test
    fun corruptFilesAreDeletedAndReported() = runTest {
        val store = store()
        store.saveTrace(trace("a"))
        val name = fs.files.keys.single()
        fs.files[name] = fs.files.getValue(name).take(40)
        assertTrue(store.loadAll().isEmpty())
        assertTrue(fs.files.isEmpty())
        assertTrue(events.any { it is StorageEvent.CorruptFile })
    }

    @Test
    fun orphanTempFilesAreRemovedOnOpen() = runTest {
        fs.files["x.jtrace.json.tmp"] = "half"
        store().saveTrace(trace("a"))
        assertTrue(fs.files.keys.none { it.endsWith(".tmp") })
    }

    @Test
    fun queryFiltersByStatusNameAndLimit() = runTest {
        val store = store()
        store.saveTrace(trace("a"))
        store.saveTrace(trace("b", wall = clock.wall + 5))
        assertEquals(1, store.query(TraceQuery(limit = 1)).size)
        assertEquals(2, store.query(TraceQuery(statuses = setOf(TraceStatus.COMPLETED), nameContains = "CHECK")).size)
        assertEquals(0, store.query(TraceQuery(statuses = setOf(TraceStatus.FAILED))).size)
    }

    @Test
    fun deleteAndClearAll() = runTest {
        val store = store()
        store.saveTrace(trace("a"))
        store.saveTrace(trace("b", wall = clock.wall + 1))
        assertTrue(store.delete(TraceId("t-a")))
        assertNull(store.load(TraceId("t-a")))
        store.clearAll()
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun ioFailuresNeverEscapeAndSuspendPersistenceAfterRepeatedErrors() = runTest {
        fs.failWrites = true
        val store = store()
        repeat(TraceStore.DEFAULT_FAILURE_THRESHOLD) { assertEquals(SaveOutcome.FAILED, store.saveTrace(trace("a$it"))) }
        assertTrue(store.isSuspended)
        assertTrue(events.any { it is StorageEvent.CircuitOpened })
        fs.failWrites = false
        assertEquals(SaveOutcome.SUSPENDED, store.saveTrace(trace("z")))
        store.resume()
        assertEquals(SaveOutcome.SAVED, store.saveTrace(trace("z")))
    }

    @Test
    fun unsafeIdsAreRejectedSoTheyCannotEscapeTheDirectory() = runTest {
        val bad = trace("a").copy(id = TraceId("../../etc/passwd"))
        assertEquals(SaveOutcome.INVALID_ID, store().saveTrace(bad))
        assertTrue(fs.files.isEmpty())
    }

    @Test
    fun concurrentSavesStayBoundedAndConsistent() = runTest {
        val store = store(ExportPolicy(persistenceEnabled = true, maxStoredTraces = 10))
        coroutineScope {
            (0 until 50).map { i -> async(Dispatchers.Default) { store.saveTrace(trace("c$i", wall = clock.wall + i)) } }.awaitAll()
        }
        val stored = store.loadAll()
        assertEquals(10, stored.size)
        assertEquals(10, fs.files.size)
        assertEquals(stored.map { it.id.value }.toSet().size, stored.size)
    }

    @Test
    fun retentionUsesInjectedClockNotRealTime() = runTest {
        val store = store(ExportPolicy(persistenceEnabled = true, retention = 1.hours))
        store.saveTrace(trace("a"))
        clock.advance(2.hours)
        store.saveTrace(trace("b", wall = clock.wall))
        assertEquals(listOf("t-b"), store.loadAll().map { it.id.value })
    }
}
