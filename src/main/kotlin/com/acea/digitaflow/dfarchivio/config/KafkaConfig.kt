package com.acea.digitaflow.dfarchivio.config

import com.acea.digitaflow.dfarchivio.request.ArchivioResponse
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaRetryTopic
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@EnableKafkaRetryTopic
@Configuration
class KafkaConfig {

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> =
        DefaultKafkaProducerFactory(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java
            )
        )

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(producerFactory())

    @Bean
    fun consumerFactory(): ConsumerFactory<String, ArchivioResponse> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-consumer-group",

                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,

                JsonDeserializer.VALUE_DEFAULT_TYPE to ArchivioResponse::class.java.name,
                JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
                JsonDeserializer.TRUSTED_PACKAGES to "com.acea.digitaflow.**",

                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
            )
        )

    @Bean
    fun archivioKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, ArchivioResponse> {

        val factory = ConcurrentKafkaListenerContainerFactory<String, ArchivioResponse>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        return factory
    }
    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 2
        scheduler.setThreadNamePrefix("kafka-retry-")
        scheduler.initialize()
        return scheduler
    }

}
