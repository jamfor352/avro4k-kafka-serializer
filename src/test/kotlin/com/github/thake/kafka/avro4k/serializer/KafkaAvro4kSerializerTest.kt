package com.github.thake.kafka.avro4k.serializer


import com.github.avrokotlin.avro4k.Avro
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify
import org.apache.avro.Schema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class KafkaAvro4kSerializerTest {
    private val registryMock = spyk(MockSchemaRegistryClient())

    companion object{
        @JvmStatic
        fun createSerializableObjects(): Stream<out Any> {
            return Stream.of(
                TestRecord("STTR"),
                TestRecordWithNull(null, 2),
                TestRecordWithNull("33", 1),
                TestRecordWithNamespace(4.0f),
                TestRecordWithManyAliases(2.0)
            )
        }
    }

    @Test
    fun testRecordSerializedWithNull() {
        val serializer = KafkaAvro4kSerializer(registryMock)
        serializer.configure(
            mapOf(
                AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to "true",
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://registry"
            ),
            false
        )
        val avroSchema = Avro.Default.schema(TestRecord.serializer().descriptor)
        val unionSchema = Schema.createUnion(Schema.create(Schema.Type.NULL), avroSchema)
        val topic = "My-Topic"
        val subjectName = "$topic-value"
        registryMock.register(subjectName, AvroSchema(unionSchema))
        val result = serializer.serialize(topic, null)
        result.shouldBe(null)
    }

    @ParameterizedTest()
    @MethodSource("createSerializableObjects")
    fun testRecordSerDeRoundtrip(toSerialize: Any?) {
        val serializer = KafkaAvro4kSerializer(registryMock)
        serializer.configure(
            mapOf(
                AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to "true",
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://registry"
            ),
            false
        )
        val topic = "My-Topic"
        val result = serializer.serialize(topic, toSerialize)
        assertNotNull(result)
        result ?: throw Exception("")
        verify {
            registryMock.register(any(), any<ParsedSchema>())
        }
        verify(inverse = true) {
            registryMock.getId("$topic-value", any<ParsedSchema>())
        }


        val deserializer = KafkaAvro4kDeserializer(
            registryMock,
            mapOf(
                KafkaAvro4kDeserializerConfig.RECORD_PACKAGES to this::class.java.`package`.name,
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "mock://registry"
            )
        )

        val deserializedValue = deserializer.deserialize(topic, result)
        assertEquals(toSerialize, deserializedValue)
    }
}
