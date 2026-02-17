package com.acea.digitaflow.dfarchivio.config

import com.acea.digitaflow.dfarchivio.request.ArchivioRequest
import com.acea.digitaflow.dfarchivio.request.MailRequest
import com.acea.digitaflow.dfarchivio.request.PostaRequest
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
    fun consumerFactory(): ConsumerFactory<String, ArchivioRequest> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-consumer-group",

                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,

                JsonDeserializer.VALUE_DEFAULT_TYPE to ArchivioRequest::class.java.name,
                JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
                JsonDeserializer.TRUSTED_PACKAGES to "com.acea.digitaflow.**",

                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
            )
        )

    @Bean
    fun archivioKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, ArchivioRequest> {

        val factory = ConcurrentKafkaListenerContainerFactory<String, ArchivioRequest>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        return factory
    }
    @Bean
    fun mailConsumerFactory(): ConsumerFactory<String, MailRequest> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-mail-consumer-group",

                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,

                JsonDeserializer.VALUE_DEFAULT_TYPE to MailRequest::class.java.name,
                JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
                JsonDeserializer.TRUSTED_PACKAGES to "com.acea.digitaflow.**,com.acea.digitalflow.**",

                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
            )
        )

    @Bean
    fun mailKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, MailRequest> {

        val factory = ConcurrentKafkaListenerContainerFactory<String, MailRequest>()
        factory.consumerFactory = mailConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        return factory
    }

    @Bean
    fun postaConsumerFactory(): ConsumerFactory<String, PostaRequest> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-posta-consumer-group",

                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,

                JsonDeserializer.VALUE_DEFAULT_TYPE to PostaRequest::class.java.name,
                JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
                JsonDeserializer.TRUSTED_PACKAGES to "com.acea.digitaflow.**,com.acea.digitalflow.**",

                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
            )
        )

    @Bean
    fun postaKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, PostaRequest> {

        val factory = ConcurrentKafkaListenerContainerFactory<String, PostaRequest>()
        factory.consumerFactory = postaConsumerFactory()
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
