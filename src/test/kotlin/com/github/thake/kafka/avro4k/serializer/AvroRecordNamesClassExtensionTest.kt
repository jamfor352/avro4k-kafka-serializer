package com.github.thake.kafka.avro4k.serializer

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class AvroRecordNamesClassExtensionTest {

    @Test
    fun usingTestRecordWithAlias_allAliasesAreReturned() {
        TestRecordWithManyAliases::class.java.avroRecordNames.shouldContainExactly(
            "ExampleSerialName",
            "ExampleAvroAliasTheFirst",
            "ExampleAvroAliasTheSecond",
            "ExampleAvroAliasTheThird",
            "ExampleApacheAvroAliasName",
            "ExampleNestedApacheAvroAliasNameTheFirst",
            "ExampleNestedApacheAvroAliasNameTheSecond",
        )
    }
}