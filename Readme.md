# Kafka avro4k serializer / deserializer
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.thake.avro4k/avro4k-kafka-serializer/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.thake.avro4k/avro4k-kafka-serializer)

This project implements a Kafka serializer / deserializer that integrates with the confluent schema registry and
leverages [avro4k](https://github.com/avro-kotlin/avro4k). It is based on confluent's [Kafka Serializer]((https://github.com/confluentinc/schema-registry/tree/master/avro-serializer)).
As such, this implementations can be used to in several projects (i.e. spring)

This SerDe supports retrying of failed calls to the schema registry (i.e. due to flaky network). Confluent's Serde does not implement this yet.
See https://github.com/confluentinc/schema-registry/issues/928.

## Confluent Versions

Version 0.10.x is compatible with Apache Kafka 2.5.x / Confluent 5.5.x and avro4k < 1.0

Version 0.11.x+ is compatible with Apache Kafka 2.6.x / Confluent 6.0.0 and avro4k < 1.0

Version > 0.13 is compatible with Apache Kafka 2.6.x/3.x (Confluent 6.x/7.x) and avro4k 1.x

## Example configuration

You can find an example configuration of a Kafka Consumer, Kafka Producer and Kafka Streams application in the [ConfluentIT](./src/integrationTest/kotlin/com/github/thake/kafka/avro4k/serializer/ConfluentIT.kt) integration test.

### Spring Cloud Stream with Kafka

```javascript
spring:
    application:
        name: <your-application>
            kafka:
            bootstrap-servers:
            -
            <your-kafka-bootstrap-server>
                cloud:
                stream:
                default:
                consumer:
                useNativeDecoding: true
                producer:
                useNativeEncoding: true
                kafka:
                streams:
                binder:
                        brokers: <your-broker>
                        configuration:
                            schema.registry.url: <your-registry>
                            schema.registry.retry.attemps: 3
                            schema.registry.retry.jitter.base: 10
                            schema.registry.retry.jitter.max: 5000
                            record.packages: <packages-of-avro4k-classes>
                            default.key.serde: com.github.thake.kafka.avro4k.serializer.Avro4kSerde
                            default.value.serde: com.github.thake.kafka.avro4k.serializer.Avro4kSerde
                    default:
                        producer:
                            keySerde: com.github.thake.kafka.avro4k.serializer.Avro4kSerde
                            valueSerde: com.github.thake.kafka.avro4k.serializer.Avro4kSerde
                        consumer:
                            keySerde: com.github.thake.kafka.avro4k.serializer.Avro4kSerde
                            valueSerde: com.github.thake.kafka.avro4k.serializer.Avro4kSerde
...
```
### Maven
```xml
<dependency>
    <groupId>com.github.thake.avro4k</groupId>
    <artifactId>avro4k-kafka-serializer</artifactId>
    <version>VERSION</version>
</dependency>
```
