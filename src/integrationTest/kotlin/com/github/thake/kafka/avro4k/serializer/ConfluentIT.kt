package com.github.thake.kafka.avro4k.serializer

import com.github.thake.kafka.avro4k.serializer.Package.PACKAGE_NAME
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.serialization.Serializable
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.awaitility.Awaitility.await
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration


const val inputTopic = "input"
const val outputTopic = "output"

@Serializable
data class Article(
    val title: String,
    val content: String
)

class KafkaStreamsIT {

    @ParameterizedTest
    @ValueSource(strings = ["7.0.15", "7.7.0"]) // latest patch of oldest supported version + newest version
    fun testConfluentIntegration(confluentVersion: String) {
        ConfluentCluster(confluentVersion).use {
            val streamsConfiguration: Properties by lazy {
                val streamsConfiguration = Properties()
                streamsConfiguration[StreamsConfig.APPLICATION_ID_CONFIG] = "specific-avro-integration-test"
                streamsConfiguration[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] =
                    it.bootstrapServers
                streamsConfiguration[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = StringSerde::class.java
                streamsConfiguration[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Avro4kSerde::class.java
                streamsConfiguration[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] =
                    it.schemaRegistryUrl
                streamsConfiguration[KafkaAvro4kDeserializerConfig.RECORD_PACKAGES] =
                    PACKAGE_NAME
                streamsConfiguration[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                streamsConfiguration
            }
            val producerConfig: Properties by lazy {
                val properties = Properties()
                properties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = it.bootstrapServers
                properties[ProducerConfig.ACKS_CONFIG] = "all"
                properties[ProducerConfig.RETRIES_CONFIG] = 0
                properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
                properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvro4kSerializer::class.java
                properties[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] =
                    it.schemaRegistryUrl
                properties
            }
            val consumerConfig: Properties by lazy {
                val properties = Properties()
                properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = it.bootstrapServers
                properties[ConsumerConfig.GROUP_ID_CONFIG] = "kafka-streams-integration-test-standard-consumer"
                properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvro4kDeserializer::class.java
                properties[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] =
                    it.schemaRegistryUrl
                properties
            }
            val admin =
                Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to it.bootstrapServers))
            //Wait for topic creations
            admin.createTopic(inputTopic)
            admin.createTopic(outputTopic)

            //Input values
            val staticInput = listOf(
                Article("Kafka Streams and Avro4k", "Just use avro4k-kafka-serializer"),
                Article("Lorem ipsum", "another content")
            )
            //Now start kafka streams
            val streamsBuilder = StreamsBuilder()
            streamsBuilder.stream<String, Article>(inputTopic).to(outputTopic)
            KafkaStreams(streamsBuilder.build(), streamsConfiguration).use {
                it.start()

                //Produce some input
                produceArticles(staticInput, producerConfig)

                //Now check output
                var values: List<KeyValue<String, Article>> = mutableListOf()
                createConsumer(consumerConfig).use {
                    await()
                        .atMost(10000.milliseconds.toJavaDuration())
                        .untilAsserted {
                            values = readValues(it)
                            values.shouldHaveSize(2)
                        }
                    values.map { it.value }.shouldContainInOrder(staticInput)
                    values.map { it.key }.shouldContainInOrder(staticInput.map { it.title })
                }
            }
        }
    }

    private fun Admin.createTopic(name: String) {
        createTopics(listOf(NewTopic(name, 1, 1))).all().get()
    }

    private fun produceArticles(articles: Collection<Article>, producerConfig: Properties) {
        val producer: Producer<String, Article> = KafkaProducer(producerConfig)
        articles.forEach { article ->
            producer.send(ProducerRecord(inputTopic, article.title, article)).get()
        }
        producer.flush()
        producer.close()
    }

    private fun createConsumer(consumerConfig: Properties): KafkaConsumer<String, Article> {
        val consumer: KafkaConsumer<String, Article> = KafkaConsumer(consumerConfig)
        consumer.subscribe(listOf(outputTopic))
        return consumer
    }

    private fun readValues(consumer: KafkaConsumer<String, Article>): List<KeyValue<String, Article>> {
        return consumer.poll(100.milliseconds.toJavaDuration())
            .map {
                KeyValue(
                    it.key(),
                    it.value())
            }
    }
}
