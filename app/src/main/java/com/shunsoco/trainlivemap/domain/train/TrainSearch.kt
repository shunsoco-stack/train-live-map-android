package com.shunsoco.trainlivemap.domain.train

import com.shunsoco.trainlivemap.data.model.TrainLocation
import java.util.Locale

/**
 * Returns at most [limit] trains whose train number starts with [query].
 * Train numbers are exposed only by the explicit search surface, never by map
 * markers or their normal accessibility descriptions.
 */
fun searchTrainsByNumberPrefix(
    trains: List<TrainLocation>,
    query: String,
    limit: Int = 5,
): List<TrainLocation> {
    require(limit >= 0) { "limit must not be negative" }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty() || limit == 0) return emptyList()

    return trains.asSequence()
        .filter { train ->
            train.trainNumber
                .trim()
                .lowercase(Locale.ROOT)
                .startsWith(normalizedQuery)
        }
        .take(limit)
        .toList()
}
