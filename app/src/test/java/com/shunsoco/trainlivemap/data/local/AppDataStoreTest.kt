package com.shunsoco.trainlivemap.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataStoreTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder.builder()
        .parentFolder(
            File(System.getProperty("user.dir"), "build/tmp/datastore-tests").apply {
                mkdirs()
            },
        )
        .assureDeletion()
        .build()

    @Test
    fun `favorites visibility and favorites-only setting persist in preferences`() = runTest {
        val store = createStore(
            file = File(temporaryFolder.root, "settings.preferences_pb"),
        )

        store.initializeVisibleLines(setOf("tokaido", "yamanote"))
        store.setFavorite("tokaido", favorite = true)
        store.setVisible("yamanote", visible = false)
        store.setFavoritesOnly(enabled = true)

        val preferences = store.preferences.first()
        assertEquals(setOf("tokaido"), preferences.favoriteLineIds)
        assertEquals(setOf("tokaido"), preferences.visibleLineIds)
        assertTrue(preferences.favoritesOnly)
        assertTrue(preferences.visibleLineIdsInitialized)

        store.initializeVisibleLines(setOf("yokosuka"))
        assertEquals(
            setOf("tokaido"),
            store.preferences.first().visibleLineIds,
        )

        store.setFavorite("tokaido", favorite = false)
        store.setFavoritesOnly(enabled = false)
        val cleared = store.preferences.first()
        assertTrue(cleared.favoriteLineIds.isEmpty())
        assertFalse(cleared.favoritesOnly)
    }

    @Test
    fun `last successful endpoint JSON snapshots are stored independently`() = runTest {
        val store = createStore(
            file = File(temporaryFolder.root, "snapshots.preferences_pb"),
        )

        store.writeTrainsJson("""{"trains":[]}""")
        store.writeServiceStatusJson("""{"serviceStatus":{}}""")
        store.writeRailwaysJson("""{"lines":[],"options":[]}""")

        assertEquals("""{"trains":[]}""", store.readTrainsJson())
        assertEquals("""{"serviceStatus":{}}""", store.readServiceStatusJson())
        assertEquals(
            """{"lines":[],"options":[]}""",
            store.readRailwaysJson(),
        )
    }

    private fun TestScope.createStore(
        file: File,
    ): AppDataStore = AppDataStore.from(
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        ),
    )
}
