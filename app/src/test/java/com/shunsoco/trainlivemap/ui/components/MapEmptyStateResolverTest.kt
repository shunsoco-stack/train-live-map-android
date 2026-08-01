package com.shunsoco.trainlivemap.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MapEmptyStateResolverTest {
    @Test
    fun loadingSuppressesAllEmptyStates() {
        assertNull(
            resolveMapEmptyState(
                loading = true,
                visibleLineCount = 0,
                trainsOnVisibleLinesCount = 0,
                filteredTrainCount = 0,
            ),
        )
    }

    @Test
    fun returnsExactlyOneReasonInPriorityOrder() {
        assertEquals(
            MapEmptyStateKind.NO_LINES,
            resolveMapEmptyState(false, 0, 0, 0),
        )
        assertEquals(
            MapEmptyStateKind.NO_TRAINS,
            resolveMapEmptyState(false, 1, 0, 0),
        )
        assertEquals(
            MapEmptyStateKind.NO_FILTER_RESULTS,
            resolveMapEmptyState(false, 1, 4, 0),
        )
        assertNull(resolveMapEmptyState(false, 1, 4, 2))
    }

    @Test
    fun rejectsImpossibleNegativeCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveMapEmptyState(false, -1, 0, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveMapEmptyState(false, 1, -1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveMapEmptyState(false, 1, 1, -1)
        }
    }
}
