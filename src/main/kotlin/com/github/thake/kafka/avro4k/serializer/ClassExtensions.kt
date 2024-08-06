package com.github.thake.kafka.avro4k.serializer


import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

/**
 * A list containing all avro record names that represent this class.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
val Class<*>.avroRecordNames: Set<String>
    get() {
        val descriptor = this.kotlin.serializer().descriptor
        val naming = RecordNaming(descriptor)
        val aliases = AnnotationExtractor(annotations.toList()).serializableNames()
        val normalNameMapping = if(naming.namespace.isNotBlank()) "${naming.namespace}.${naming.name}" else naming.name
        return if (aliases.isNotEmpty()) {
            val mappings = mutableSetOf(normalNameMapping)
            aliases.forEach { alias ->
                mappings.add(
                    if (alias.contains('.') || naming.namespace.isBlank()) {
                        alias
                    } else {
                        "${naming.namespace}.$alias"
                    }
                )
            }
            mappings
        } else {
            setOf(normalNameMapping)
        }
    }
