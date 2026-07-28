package com.shunsoco.trainlivemap.domain.railway

import com.shunsoco.trainlivemap.data.model.RailwayCoverage
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.data.model.RailwayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RailwayFilteringTest {
    private val searchableValues = listOf(
        "tokaido",
        "東海道線",
        "JR東日本",
        "東海道本線",
        "Tokaido Line",
    )

    @Test
    fun `search matches Japanese line name`() {
        assertTrue(matchesRailwayQuery("東海道", searchableValues))
    }

    @Test
    fun `search matches aliases case-insensitively and ignores spaces`() {
        assertTrue(matchesRailwayQuery("TOKAIDOline", searchableValues))
    }

    @Test
    fun `search rejects an unrelated line`() {
        assertFalse(matchesRailwayQuery("山手線", searchableValues))
    }

    @Test
    fun `blank search matches every line`() {
        assertTrue(matchesRailwayQuery("  ", emptyList()))
    }

    @Test
    fun `visibility toggle adds then removes the same line`() {
        val shown = toggleRailwayVisibility(emptySet(), "tokaido")
        assertEquals(setOf("tokaido"), shown)

        val hidden = toggleRailwayVisibility(shown, "tokaido")
        assertTrue(hidden.isEmpty())
    }

    @Test
    fun `favorite toggle preserves other favorites`() {
        val before = setOf("yamanote")
        val after = toggleRailwayFavorite(before, "tokaido")

        assertEquals(setOf("yamanote", "tokaido"), after)
    }

    @Test
    fun `favorite only filter returns favorites that also match search`() {
        val options = listOf(
            railway(id = "tokaido", name = "東海道線", aliases = listOf("Tokaido")),
            railway(id = "yamanote", name = "山手線", aliases = listOf("Yamanote")),
            railway(id = "chuo", name = "中央線", aliases = listOf("Chuo")),
        )

        val result = filterRailways(
            options = options,
            query = "線",
            favoriteOnly = true,
            favoriteLineIds = setOf("tokaido", "chuo"),
        )

        assertEquals(listOf("tokaido", "chuo"), result.map { it.id })
    }

    @Test
    fun `favorite only with no favorites is empty`() {
        val result = filterRailways(
            options = listOf(railway(id = "tokaido", name = "東海道線")),
            query = "",
            favoriteOnly = true,
            favoriteLineIds = emptySet(),
        )

        assertTrue(result.isEmpty())
    }

    private fun railway(
        id: String,
        name: String,
        aliases: List<String> = emptyList(),
    ): RailwayFilterOption = RailwayFilterOption(
        id = id,
        name = name,
        category = "JR東日本",
        color = "#f68b1e",
        aliases = aliases,
        coverage = RailwayCoverage.REALTIME,
        coverageNote = null,
        kind = RailwayKind.LINE,
        available = true,
    )
}
