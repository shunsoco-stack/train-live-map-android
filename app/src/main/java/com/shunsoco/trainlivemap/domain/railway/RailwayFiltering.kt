package com.shunsoco.trainlivemap.domain.railway

import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.data.model.RailwayMapLine
import java.util.Locale

private fun String.normalizedForSearch(): String = trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), "")

fun matchesRailwayQuery(
    option: RailwayFilterOption,
    query: String,
): Boolean = matchesRailwayQuery(
    query = query,
    searchableValues = buildList {
        add(option.id)
        add(option.name)
        add(option.category)
        addAll(option.aliases)
    },
)

fun matchesRailwayQuery(
    query: String,
    searchableValues: Iterable<String>,
): Boolean {
    val normalizedQuery = query.normalizedForSearch()
    if (normalizedQuery.isEmpty()) return true

    return searchableValues.any { candidate ->
        candidate.normalizedForSearch().contains(normalizedQuery)
    }
}

/**
 * Applies text search and the "favorites only" switch while preserving the
 * server-provided order and availability/coverage metadata.
 */
fun filterRailways(
    options: List<RailwayFilterOption>,
    query: String,
    favoriteOnly: Boolean,
    favoriteLineIds: Set<String>,
): List<RailwayFilterOption> = options.filter { option ->
    (!favoriteOnly || option.id in favoriteLineIds) &&
        matchesRailwayQuery(option, query)
}

fun toggleRailwayId(
    selectedLineIds: Set<String>,
    lineId: String,
): Set<String> = selectedLineIds.toMutableSet().apply {
    if (!add(lineId)) remove(lineId)
}.toSet()

fun toggleRailwayVisibility(
    visibleLineIds: Set<String>,
    lineId: String,
): Set<String> = toggleRailwayId(visibleLineIds, lineId)

fun toggleRailwayFavorite(
    favoriteLineIds: Set<String>,
    lineId: String,
): Set<String> = toggleRailwayId(favoriteLineIds, lineId)

fun visibleRailwayLines(
    lines: List<RailwayMapLine>,
    visibleLineIds: Set<String>,
): List<RailwayMapLine> = lines.filter { it.id in visibleLineIds }
