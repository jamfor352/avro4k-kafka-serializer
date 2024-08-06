package com.github.thake.kafka.avro4k.serializer


import com.github.thake.kafka.avro4k.serializer.Package.PACKAGE_NAME
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class Avro4kSerdeTest {

    companion object{
        @JvmStatic
        fun createSerializableObjects(): Stream<out Any> {
            return Stream.of(
                TestRecord("STTR"),
                TestRecordWithNull(null, 2),
                TestRecordWithNull("33", 1),
                TestRecordWithNamespace(4f),
                TestRecordWithDifferentName(2.0),
                1,
                2.0,
                "STR",
                true,
                2.0f,
                1L,
                byteArrayOf(0xC, 0xA, 0xF, 0xE)
            )
        }
    }

    @ParameterizedTest()
    @MethodSource("createSerializableObjects")
    fun testRecordSerDeRoundtrip(toSerialize: Any) {
        val config = mapOf(
            KafkaAvro4kDeserializerConfig.RECORD_PACKAGES to PACKAGE_NAME,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://registry"
        )
        val serde = Avro4kSerde<Any>()
        val topic = "My-Topic"
        serde.configure(config, false)
        val result = serde.serializer().serialize(topic, toSerialize)

        Assertions.assertNotNull(result)
        result ?: throw Exception("")

        val deserializedValue = serde.deserializer().deserialize(topic, result)
        if (toSerialize is ByteArray) {
            deserializedValue.shouldBeInstanceOf<ByteArray>()
            toSerialize.forEachIndexed { i, value ->
                assertEquals(value, deserializedValue[i])
            }
        } else {
            assertEquals(toSerialize, deserializedValue)
        }
    }
}
