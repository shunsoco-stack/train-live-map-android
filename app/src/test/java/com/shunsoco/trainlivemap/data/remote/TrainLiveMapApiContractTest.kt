package com.shunsoco.trainlivemap.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainLiveMapApiContractTest {
    @Test
    fun `train and service endpoints expose the lines query`() {
        assertLinesQuery(methodName = "getTrains", expectedPath = "api/trains")
        assertLinesQuery(
            methodName = "getServiceStatus",
            expectedPath = "api/service-status",
        )
    }

    @Test
    fun `community report GET and POST match the Web API contract`() {
        val get = TrainLiveMapApi::class.java.declaredMethods
            .single { it.name == "getCommunityReports" }
        assertEquals(
            "api/community-reports",
            requireNotNull(get.getAnnotation(GET::class.java)).value,
        )

        val post = TrainLiveMapApi::class.java.declaredMethods
            .single { it.name == "submitCommunityReport" }
        assertEquals(
            "api/community-reports",
            requireNotNull(post.getAnnotation(POST::class.java)).value,
        )
        assertTrue(
            requireNotNull(post.getAnnotation(Headers::class.java)).value
                .contains("Content-Type: application/json"),
        )

        val reporterHeader = post.parameterAnnotations[0]
            .filterIsInstance<Header>()
            .single()
        assertEquals("X-Community-Reporter", reporterHeader.value)
        assertNotNull(
            post.parameterAnnotations[1]
                .filterIsInstance<Body>()
                .singleOrNull(),
        )
    }

    private fun assertLinesQuery(
        methodName: String,
        expectedPath: String,
    ) {
        val method = TrainLiveMapApi::class.java.declaredMethods
            .single { it.name == methodName }
        assertEquals(
            expectedPath,
            requireNotNull(method.getAnnotation(GET::class.java)).value,
        )
        val query = method.parameterAnnotations[0]
            .filterIsInstance<Query>()
            .single()
        assertEquals("lines", query.value)
    }
}
