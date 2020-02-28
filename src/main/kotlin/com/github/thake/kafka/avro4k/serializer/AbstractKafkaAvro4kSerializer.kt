package com.github.thake.kafka.avro4k.serializer

import com.sksamuel.avro4k.Avro
import com.sksamuel.avro4k.io.AvroFormat
import com.sksamuel.avro4k.io.AvroOutputStream
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDe
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.serializers.NonRecordContainer
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.BinaryEncoder
import org.apache.avro.io.DatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.errors.SerializationException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

abstract class AbstractKafkaAvro4kSerializer : AbstractKafkaAvroSerDe() {
    private var autoRegisterSchema = false
    protected fun configure(config: KafkaAvro4kSerializerConfig) {
        configureClientProperties(config)
        autoRegisterSchema = config.autoRegisterSchema()
    }

    protected fun serializerConfig(props: Map<String, *>): KafkaAvro4kSerializerConfig {
        return KafkaAvro4kSerializerConfig(props)
    }

    @ImplicitReflectionSerializer
    @Throws(SerializationException::class)
    protected fun serializeImpl(subject: String?, obj: Any?): ByteArray? {
        return if (obj == null) {
            null
        } else {
            try {
                val schema = Avro4kSchemaUtils.getSchema(obj)
                val id = getSchemaId(subject, schema)
                val out = ByteArrayOutputStream()
                writeSchemaId(out, id)
                if (obj is ByteArray) {
                    out.write(obj)
                } else {
                    val value =
                        if (obj is NonRecordContainer) obj.value else obj
                    Avro.default.openOutputStream(value::class.serializer() as KSerializer<Any>) {
                        format = AvroFormat.BinaryFormat
                    }.to(out).write(obj).close()
                }
                val bytes = out.toByteArray()
                out.close()
                bytes
            } catch (re: RuntimeException) {
                throw SerializationException("Error serializing Avro message with avro4k", re)
            } catch (io: IOException) {
                throw SerializationException("Error serializing Avro message with avro4k", io)
            }
        }
    }

    private fun writeSchemaId(out: ByteArrayOutputStream, id: Int) {
        out.write(0)
        out.write(ByteBuffer.allocate(4).putInt(id).array())
    }

    private fun getSchemaId(
        subject: String?,
        schema: Schema?
    ): Int {
        return if (autoRegisterSchema) {
            try {
                schemaRegistry.register(subject, schema)
            }catch (e : RestClientException){
                throw SerializationException("Error registering Avro schema in schema registry: "+schema,e)
            }
        } else {
            try {
                schemaRegistry.getId(subject, schema)
            }catch(e : RestClientException){
                throw SerializationException("Error retrieving Avro schema from schema registry: "+schema,e)
            }
        }
    }
}