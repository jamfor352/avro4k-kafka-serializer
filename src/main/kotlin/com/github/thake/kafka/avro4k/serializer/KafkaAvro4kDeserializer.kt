package com.github.thake.kafka.avro4k.serializer

import com.github.avrokotlin.avro4k.Avro
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.apache.kafka.common.serialization.Deserializer

class KafkaAvro4kDeserializer(
    client: SchemaRegistryClient? = null,
    props: Map<String, *>? = null,
    avro: Avro = Avro.Default
) : AbstractKafkaAvro4kDeserializer(avro), Deserializer<Any?> {
    private var isKey = false

    init {
        props?.let { configure(this.deserializerConfig(it)) }
        //Set the registry client explicitly after the configuration has been applied to override client from configuration
        if (client != null) this.schemaRegistry = client
    }

    override fun configure(configs: Map<String, *>, isKey: Boolean) {
        this.isKey = isKey
        this.configure(KafkaAvro4kDeserializerConfig(configs))
    }


    override fun deserialize(s: String?, bytes: ByteArray?): Any? {
        return this.deserialize(s, bytes, null)
    }

    fun deserialize(@Suppress("UNUSED_PARAMETER") topic: String?, data: ByteArray?, readerSchema: Schema?): Any? {
        return this.deserialize(data, readerSchema)
    }

    override fun close() {}
}
