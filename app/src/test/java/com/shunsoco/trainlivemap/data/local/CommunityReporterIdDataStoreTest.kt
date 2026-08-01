package com.shunsoco.trainlivemap.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CommunityReporterIdDataStoreTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder.builder()
        .parentFolder(
            File(System.getProperty("user.dir"), "build/tmp/voter-id-tests").apply {
                mkdirs()
            },
        )
        .assureDeletion()
        .build()

    @Test
    fun `validation accepts only 12 to 100 ASCII identifier characters`() {
        assertFalse(isValidCommunityReporterId("a".repeat(11)))
        assertTrue(isValidCommunityReporterId("a".repeat(12)))
        assertTrue(isValidCommunityReporterId("AZaz09_-" + "x".repeat(92)))
        assertFalse(isValidCommunityReporterId("a".repeat(101)))
        assertFalse(isValidCommunityReporterId("abcdefghijkl!"))
        assertFalse(isValidCommunityReporterId("１２３４５６７８９０１２"))
        assertFalse(isValidCommunityReporterId("abcdefghijkl\n"))
    }

    @Test
    fun `valid generated ID persists across store instances`() = runTest {
        val file = File(temporaryFolder.root, "community.preferences_pb")
        val generated = "reporter_1234567890"
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val first = CommunityReporterIdDataStore.from(dataStore) { generated }

        assertEquals(generated, first.getOrCreateVoterId())
        val second = CommunityReporterIdDataStore.from(dataStore) {
            error("persisted ID should be reused")
        }
        assertEquals(generated, second.getOrCreateVoterId())
    }

    @Test
    fun `invalid persisted ID is atomically replaced`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "invalid.preferences_pb") },
        )
        dataStore.edit { values ->
            values[CommunityReporterIdDataStore.REPORTER_ID_KEY] = "invalid id"
        }
        val replacement = "replacement_12345"
        val store = CommunityReporterIdDataStore.from(dataStore) { replacement }

        assertEquals(replacement, store.getOrCreateVoterId())
        assertEquals(replacement, store.getOrCreateVoterId())
    }

    @Test
    fun `invalid generated ID is rejected instead of being persisted`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "bad-generator.preferences_pb") },
        )
        val invalid = CommunityReporterIdDataStore.from(dataStore) { "too-short" }

        var rejected = false
        try {
            invalid.getOrCreateVoterId()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)

        val valid = CommunityReporterIdDataStore.from(dataStore) { "valid_reporter_123" }
        assertEquals("valid_reporter_123", valid.getOrCreateVoterId())
    }

    @Test
    fun `concurrent readers share one atomically generated ID`() = runTest {
        val generations = AtomicInteger(0)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "concurrent.preferences_pb") },
        )
        val store = CommunityReporterIdDataStore.from(dataStore) {
            "reporter_${generations.incrementAndGet().toString().padStart(12, '0')}"
        }

        val ids = List(20) {
            async { store.getOrCreateVoterId() }
        }.awaitAll()

        assertEquals(1, generations.get())
        assertEquals(1, ids.distinct().size)
        assertTrue(isValidCommunityReporterId(ids.first()))
    }
}
