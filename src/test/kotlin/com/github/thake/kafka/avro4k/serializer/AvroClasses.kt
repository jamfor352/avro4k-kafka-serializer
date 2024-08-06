package com.github.thake.kafka.avro4k.serializer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TestRecord(
    val str : String
)
@Serializable
data class TestRecordWithNull(
    val nullableStr : String? = null,
    val intValue : Int
)
@Serializable
@SerialName("custom.namespace.serde.TestRecordWithNamespace")
data class TestRecordWithNamespace(
    val float : Float
)
@Serializable
@SerialName("AnotherName")
data class TestRecordWithDifferentName(
    val double : Double
)
