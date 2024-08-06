package com.github.thake.kafka.avro4k.serializer

import com.github.avrokotlin.avro4k.AvroAlias
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.avro.reflect.AvroAliases

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
@SerialName("ExampleSerialName")
@AvroAlias("ExampleAvroAliasTheFirst", "ExampleAvroAliasTheSecond", "ExampleAvroAliasTheThird")
@org.apache.avro.reflect.AvroAlias(alias = "ExampleApacheAvroAliasName")
@AvroAliases(
    org.apache.avro.reflect.AvroAlias(alias = "ExampleNestedApacheAvroAliasNameTheFirst"),
    org.apache.avro.reflect.AvroAlias(alias = "ExampleNestedApacheAvroAliasNameTheSecond")
)
data class TestRecordWithManyAliases(
    val double : Double
)
