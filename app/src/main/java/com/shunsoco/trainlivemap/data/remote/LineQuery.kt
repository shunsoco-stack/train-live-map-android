package com.shunsoco.trainlivemap.data.remote

/**
 * Produces the canonical value used by the backend's `lines` query parameter.
 *
 * A single canonical representation is shared by Retrofit requests and the
 * snapshot cache scope, so a different ordering of the same selected lines
 * cannot create a false cache miss. An explicitly empty selection remains an
 * empty string (`?lines=`); only the compatibility no-argument API omits the
 * query parameter.
 */
fun normalizeLinesQuery(lineIds: Iterable<String>): String = lineIds
    .asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .sorted()
    .joinToString(",")
