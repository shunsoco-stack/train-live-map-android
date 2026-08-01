package com.shunsoco.trainlivemap.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class LineQueryTest {
    @Test
    fun `selected lines are trimmed deduplicated and sorted`() {
        assertEquals(
            "joban,tokaido,yamanote",
            normalizeLinesQuery(
                listOf(" yamanote ", "tokaido", "joban", "tokaido", ""),
            ),
        )
    }

    @Test
    fun `empty selection remains an explicit empty query value`() {
        assertEquals("", normalizeLinesQuery(emptySet()))
        assertEquals("", normalizeLinesQuery(setOf(" ")))
    }
}
