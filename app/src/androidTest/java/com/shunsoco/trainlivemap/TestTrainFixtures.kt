package com.shunsoco.trainlivemap

import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType

fun testTrain(
    id: String = "train-1000g",
    trainNumber: String = "1000G",
    dataAccuracy: DataAccuracy = DataAccuracy.ESTIMATED,
): TrainLocation = TrainLocation(
    id = id,
    lineId = "yamanote",
    lineName = "山手線",
    lineColor = "#9acd32",
    trainNumber = trainNumber,
    direction = TrainDirection.INBOUND,
    destination = "東京",
    trainType = TrainType.LOCAL,
    latitude = 35.6812,
    longitude = 139.7671,
    delayMinutes = 0,
    speedKmh = 35.0,
    status = TrainStatus.RUNNING,
    lastUpdatedAt = "2026-08-01T00:00:00Z",
    stoppedSince = null,
    dataAccuracy = dataAccuracy,
    routeSegment = null,
)
