package com.github.thake.kafka.avro4k.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.SerialDescriptor

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
        if(annotations.any { it is SerialName }){
            annotations.filterIsInstance<SerialName>().map { it.value }
        }else{
            emptyList()
        }
}
