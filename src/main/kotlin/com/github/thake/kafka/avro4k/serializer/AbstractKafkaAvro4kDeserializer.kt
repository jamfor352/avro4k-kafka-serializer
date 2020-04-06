package com.github.thake.kafka.avro4k.serializer

import com.sksamuel.avro4k.*
import com.sksamuel.avro4k.io.AvroFormat
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDe
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.apache.avro.Schema
import org.apache.avro.specific.SpecificData
import org.apache.kafka.common.errors.SerializationException
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import java.io.IOException
import java.nio.ByteBuffer

@ImplicitReflectionSerializer
abstract class AbstractKafkaAvro4kDeserializer : AbstractKafkaAvroSerDe() {
    private var specificLookupForClassLoader: MutableMap<ClassLoader, Lookup> = mutableMapOf()
    private var recordPackages: List<String>? = null

    inner class Lookup(private val classLoader: ClassLoader) {
        private val specificData = SpecificData(classLoader)
        private val recordNameToType: Map<String, Class<*>> by lazy {
            val currentPackages = recordPackages ?: throw IllegalStateException(
                "Couldn't find record by schema name. " +
                        "Tried to loookup types with @AvroName, @AvroNamespace and @AvroAliases annotations but either config parameter" +
                        "'${KafkaAvro4kDeserializerConfig.RECORD_PACKAGES}' has not been set, or config has not been called."
            )
            if (currentPackages.isNotEmpty()) {
                val configBuilder = ConfigurationBuilder().addScanners(TypeAnnotationsScanner(), SubTypesScanner())
                currentPackages.forEach { configBuilder.addUrls((ClasspathHelper.forPackage(it, classLoader))) }

                val reflection = Reflections(configBuilder)
                val avroTypes = HashSet<Class<*>>().apply {
                    this.addAll(reflection.getTypesAnnotatedWith(AvroName::class.java, true))
                    this.addAll(reflection.getTypesAnnotatedWith(AvroNamespace::class.java, true))
                    this.addAll(reflection.getTypesAnnotatedWith(AvroAlias::class.java, true))
                    this.addAll(reflection.getTypesAnnotatedWith(AvroAliases::class.java, true))
                }
                avroTypes.flatMap { type ->
                    getTypeNames(type).map { Pair(it, type) }
                }.toMap()
            } else {
                emptyMap()
            }
        }

        fun lookupType(msgSchema: Schema): Class<*>? {
            //Use the context classloader to not run into https://github.com/spring-projects/spring-boot/issues/14622 when
            //using Spring boot devtools
            var objectClass: Class<*>? = specificData.getClass(msgSchema)
            if (objectClass == null) {
                objectClass = recordNameToType[msgSchema.fullName]
            }
            return objectClass
        }
    }

    //A map of alternative names for types that are annotated with Avro annotations


    protected fun getTypeNames(type: Class<*>): List<String> {
        val descriptor = type.kotlin.serializer().descriptor
        val naming = RecordNaming(descriptor)
        val aliases = AnnotationExtractor(descriptor.getEntityAnnotations()).aliases()
        val normalNameMapping = "${naming.namespace()}.${naming.name()}"
        return if (aliases.isNotEmpty()) {
            val mappings = mutableListOf(normalNameMapping)
            aliases.forEach {alias ->
                mappings.add(if (alias.contains('.')) {
                    alias
                } else {
                    "${naming.namespace()}.$alias"
                })
            }
            mappings
        } else {
            listOf(normalNameMapping)
        }
    }
    protected fun configure(config: KafkaAvro4kDeserializerConfig) {
        val configuredPackages = config.getRecordPackages()
        if (configuredPackages.isEmpty()) {
            throw IllegalArgumentException("${KafkaAvro4kDeserializerConfig.RECORD_PACKAGES} is not set correctly.")
        }
        recordPackages = configuredPackages
        configureClientProperties(config)
    }

    protected fun deserializerConfig(props: Map<String, *>): KafkaAvro4kDeserializerConfig {
        return KafkaAvro4kDeserializerConfig(props)
    }


    private fun getByteBuffer(payload: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.wrap(payload)
        return if (buffer.get().toInt() != 0) {
            throw SerializationException("Unknown magic byte!")
        } else {
            buffer
        }
    }

    @Throws(SerializationException::class)
    protected fun deserialize(
        payload: ByteArray?
    ): Any? {

        return if (payload == null) {
            null
        } else {
            var id = -1
            try {
                val buffer = getByteBuffer(payload)
                id = buffer.int
                val schema = schemaRegistry.getById(id)
                val length = buffer.limit() - 1 - 4
                val bytes = ByteArray(length)
                buffer[bytes, 0, length]
                return deserialize(schema, bytes)
            } catch (re: RuntimeException) {
                throw SerializationException("Error deserializing Avro message for schema id $id with avro4k", re)
            } catch (io: IOException) {
                throw SerializationException("Error deserializing Avro message for schema id $id with avro4k", io)
            } catch (registry: RestClientException) {
                throw SerializationException("Error retrieving Avro schema for id $id from schema registry.", registry)
            }
        }
    }

    fun deserialize(schema: Schema, bytes: ByteArray) =
        when (schema.type) {
            Schema.Type.BYTES -> bytes
            Schema.Type.ARRAY -> {

            }
            else -> {
                val reader = getSerializer(schema)
                Avro.default.openInputStream(reader) {
                    format = AvroFormat.BinaryFormat
                    writerSchema = schema
                }.from(bytes).nextOrThrow()
            }
        }


    protected open fun getSerializer(msgSchema: Schema): KSerializer<*> {
        //First lookup using the context class loader
        val contextClassLoader = Thread.currentThread().contextClassLoader
        var objectClass: Class<*>? = null
        if (contextClassLoader != null) {
            objectClass = getLookup(contextClassLoader).lookupType(msgSchema)
        }
        if (objectClass == null) {
            //Fallback to classloader of this class
            objectClass = getLookup(AbstractKafkaAvro4kDeserializer::class.java.classLoader).lookupType(msgSchema)
                ?: throw SerializationException("Couldn't find matching class for record type ${msgSchema.fullName}. Full schema: $msgSchema")
        }

        return objectClass.kotlin.serializer()
    }

    private fun getLookup(classLoader: ClassLoader) =
        specificLookupForClassLoader.getOrPut(classLoader,
            { Lookup(classLoader) })


}