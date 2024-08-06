package com.github.thake.kafka.avro4k.serializer

import com.github.avrokotlin.avro4k.AvroAlias
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.SerialDescriptor
import org.apache.avro.reflect.AvroAliases

/**
 * original authors:
 * @author avro-kotlin/avro4k team
 *
 * modified by (copied in here after removal of the original class from the avro4k library):
 * @author jamfor352
 */
@ExperimentalSerializationApi
class AnnotationExtractor(private val annotations: List<Annotation>) {

    companion object {

        operator fun invoke(descriptor: SerialDescriptor, index: Int): AnnotationExtractor =
            AnnotationExtractor(descriptor.getElementAnnotations(index))
    }

    fun namespace(): String? =
        // get the @SerialName annotation and get the namespace from it
        annotations.find { it is SerialName }?.let { (it as SerialName).value.split('.').dropLast(1).joinToString(".") }

    fun name(): String? =
        // get the @SerialName annotation and get the name from it
        annotations.find { it is SerialName }?.let { (it as SerialName).value.split('.').last() }
    fun serializableNames(): List<String> =
        annotations.filterIsInstance<SerialName>().map { it.value } +
            annotations.filterIsInstance<AvroAlias>().flatMap { it.value.toList() } +
                annotations.filterIsInstance<org.apache.avro.reflect.AvroAlias>().map { it.alias } +
                    annotations.filterIsInstance<AvroAliases>().map { it.value.map { it.alias } }.flatten()
}
