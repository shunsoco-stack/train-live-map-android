package com.shunsoco.trainlivemap.domain.service

import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStatusPolicyTest {
    @Test
    fun `resumed service with remaining delays is minor rather than suspended`() {
        assertEquals(
            ServiceSeverity.MINOR,
            classifyServiceStatusSeverity(
                "運転を見合わせていましたが、運転を再開し、一部列車に遅れがでています。",
            ),
        )
    }

    @Test
    fun `resumed service without a remaining disruption is normal`() {
        assertEquals(
            ServiceSeverity.NORMAL,
            classifyServiceStatusSeverity("運転を見合わせていましたが、運転再開しました。"),
        )
    }

    @Test
    fun `planned resumption does not mean service has resumed`() {
        listOf(
            "運転再開の見込みは立っていません。現在も運転を見合わせています。",
            "15時頃に運転を再開する見込みです。",
            "運転を再開する予定です。",
            "運転再開は15時頃を見込んでいます。",
        ).forEach { message ->
            assertEquals(
                message,
                ServiceSeverity.MAJOR,
                classifyServiceStatusSeverity(message),
            )
        }
    }

    @Test
    fun `later completed resumption wins over an earlier plan`() {
        assertEquals(
            ServiceSeverity.NORMAL,
            classifyServiceStatusSeverity(
                "運転再開は15時頃を予定していましたが、14時50分に運転を再開しました。",
            ),
        )
    }

    @Test
    fun `later renewed suspension wins over an earlier resumption`() {
        assertEquals(
            ServiceSeverity.MAJOR,
            classifyServiceStatusSeverity(
                "いったん運転を再開しましたが、再度運転を見合わせています。",
            ),
        )
    }

    @Test
    fun `active suspension wording is major`() {
        assertEquals(
            ServiceSeverity.MAJOR,
            classifyServiceStatusSeverity("人身事故の影響で、上下線で運転を見合わせています。"),
        )
    }

    @Test
    fun `through service cancellation is minor rather than a full suspension`() {
        assertEquals(
            ServiceSeverity.MINOR,
            classifyServiceStatusSeverity(
                "一部列車に遅れがでています。川越線への直通運転を中止しています。",
            ),
        )
    }

    @Test
    fun `explicit major severity wins even when text mentions resumption`() {
        val official = status(
            severity = ServiceSeverity.MAJOR,
            message = "運転再開の見込みは立っていません。運転を見合わせています。",
        )

        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = official,
            trains = listOf(train("delayed", delayMinutes = 72)),
            nowMillis = NOW_MILLIS,
        )

        assertSame(official, result)
    }

    @Test
    fun `explicit minor severity is not promoted by local prose classification`() {
        val official = status(
            severity = ServiceSeverity.MINOR,
            message = "運転見合わせという過去の案内を更新し、一部列車に遅れがでています。",
        )

        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = official,
            trains = listOf(train("delayed", delayMinutes = 72)),
            nowMillis = NOW_MILLIS,
        )

        assertSame(official, result)
    }

    @Test
    fun `explicit ODPT disruption always wins over train delay fallback`() {
        listOf(ServiceSeverity.MINOR, ServiceSeverity.MAJOR).forEach { severity ->
            val official = status(
                severity = severity,
                message = if (severity == ServiceSeverity.MAJOR) {
                    "人身事故の影響で運転を見合わせています。"
                } else {
                    "一部列車に遅れがでています。"
                },
            )

            val result = serviceStatusWithTrainDelayFallback(
                serviceStatus = official,
                trains = listOf(train("delayed", delayMinutes = 72)),
                nowMillis = NOW_MILLIS,
            )

            assertSame(official, result)
        }
    }

    @Test
    fun `recent train delay cancels normal display and shows maximum delay`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("delay-5", delayMinutes = 5, updatedAt = "2026-07-31T08:44:50Z"),
                train("delay-12", delayMinutes = 12, updatedAt = "2026-07-31T08:45:10Z"),
                train("on-time", delayMinutes = 0),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, result.severity)
        assertEquals(
            "列車位置情報では最大12分程度の遅れが確認されています。",
            result.message,
        )
        assertEquals("2026-07-31T08:45:10Z", result.updatedAt)
        assertEquals(DataAccuracy.ESTIMATED, result.dataAccuracy)
    }

    @Test
    fun `maximum delay of thirty minutes is major even for one train`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("delay-30", delayMinutes = 30),
                train("on-time-1", delayMinutes = 0),
                train("on-time-2", delayMinutes = 0),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MAJOR, result.severity)
        assertEquals(
            "列車位置情報では最大30分の大幅な遅れが確認されています。" +
                "公式の運行情報もあわせてご確認ください。",
            result.message,
        )
    }

    @Test
    fun `fifteen minute delay with half of trains delayed is major`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("delay-15", delayMinutes = 15),
                train("delay-16", delayMinutes = 16),
                train("on-time-1", delayMinutes = 0),
                train("on-time-2", delayMinutes = 0),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MAJOR, result.severity)
    }

    @Test
    fun `only trains delayed at least fifteen minutes count toward major ratio`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("delay-15", delayMinutes = 15),
                train("delay-1", delayMinutes = 1),
                train("on-time-1", delayMinutes = 0),
                train("on-time-2", delayMinutes = 0),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, result.severity)
    }

    @Test
    fun `delayed status and positive delay minutes independently cancel normal display`() {
        val delayedWithoutMinutes = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("status-delayed", delayMinutes = 0, status = TrainStatus.DELAYED),
            ),
            nowMillis = NOW_MILLIS,
        )
        val runningWithMinutes = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("minutes-delayed", delayMinutes = 4, status = TrainStatus.RUNNING),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, delayedWithoutMinutes.severity)
        assertEquals(ServiceSeverity.MINOR, runningWithMinutes.severity)
    }

    @Test
    fun `twenty nine minute delay below the ratio threshold stays minor`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("delay-29", delayMinutes = 29),
                train("on-time-1", delayMinutes = 0),
                train("on-time-2", delayMinutes = 0),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, result.severity)
    }

    @Test
    fun `train exactly two minutes old is eligible`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train(
                    id = "boundary",
                    delayMinutes = 8,
                    updatedAt = "2026-07-31T08:43:30Z",
                ),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, result.severity)
    }

    @Test
    fun `train exactly thirty seconds in the future is eligible`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train(
                    id = "future-boundary",
                    delayMinutes = 8,
                    updatedAt = "2026-07-31T08:46:00Z",
                ),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, result.severity)
    }

    @Test
    fun `old invalid future and other line trains are excluded`() {
        val official = status()
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = official,
            trains = listOf(
                train("old", delayMinutes = 72, updatedAt = "2026-07-31T08:43:29.999Z"),
                train("invalid", delayMinutes = 72, updatedAt = "not-a-date"),
                train("future", delayMinutes = 72, updatedAt = "2026-07-31T08:46:00.001Z"),
                train("other", delayMinutes = 72, lineId = "yamanote"),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertSame(official, result)
    }

    @Test
    fun `stale large delay does not contaminate a fresh small delay`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(
                train("stale-72", delayMinutes = 72, updatedAt = "2026-07-31T08:40:00Z"),
                train("fresh-5", delayMinutes = 5),
            ),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, result.severity)
        assertTrue(result.message.contains("最大5分"))
    }

    @Test
    fun `delay fallback never claims that service is suspended`() {
        val result = serviceStatusWithTrainDelayFallback(
            serviceStatus = status(),
            trains = listOf(train("delay-120", delayMinutes = 120)),
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MAJOR, result.severity)
        assertFalse(result.message.contains("見合わせ"))
        assertFalse(result.message.contains("運休"))
        assertTrue(result.message.contains("大幅な遅れ"))
    }

    private fun status(
        severity: ServiceSeverity = ServiceSeverity.NORMAL,
        message: String = "平常どおり運転しています。",
    ) = ServiceStatus(
        lineId = "saikyo",
        lineName = "埼京線",
        severity = severity,
        message = message,
        updatedAt = "2026-07-31T08:44:00Z",
        dataAccuracy = DataAccuracy.ACTUAL,
    )

    private fun train(
        id: String,
        delayMinutes: Int,
        updatedAt: String = "2026-07-31T08:45:00Z",
        lineId: String = "saikyo",
        status: TrainStatus = if (delayMinutes > 0) TrainStatus.DELAYED else TrainStatus.RUNNING,
    ) = TrainLocation(
        id = id,
        lineId = lineId,
        lineName = if (lineId == "saikyo") "埼京線" else "山手線",
        lineColor = "#00ac9a",
        trainNumber = id,
        direction = TrainDirection.INBOUND,
        destination = "大宮",
        trainType = TrainType.LOCAL,
        latitude = 35.7,
        longitude = 139.7,
        delayMinutes = delayMinutes,
        speedKmh = 0.0,
        status = status,
        lastUpdatedAt = updatedAt,
        stoppedSince = null,
        dataAccuracy = DataAccuracy.ACTUAL,
        routeSegment = null,
    )

    private companion object {
        val NOW_MILLIS: Long = Instant.parse("2026-07-31T08:45:30Z").toEpochMilli()
    }
}
