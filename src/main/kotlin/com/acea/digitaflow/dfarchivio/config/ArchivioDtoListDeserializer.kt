package com.acea.digitaflow.dfarchivio.config

import com.acea.digitalflow.df_common.dto.ArchivioDto
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.dto.PostaDto
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaRetryTopic
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

// ==================== CUSTOM DESERIALIZERS ====================

/**
 * Deserializer per liste di ArchivioDto
 */
class ArchivioDtoListDeserializer : Deserializer<List<ArchivioDto>> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())

    override fun deserialize(topic: String?, data: ByteArray?): List<ArchivioDto>? {
        if (data == null) return null

        return try {
            objectMapper.readValue<List<ArchivioDto>>(data)
        } catch (e: Exception) {
            log.error("Errore deserializzazione lista ArchivioDto: ${e.message}", e)
            throw e
        }
    }

    override fun close() {}
}

/**
 * Deserializer per liste di MailDto
 */
class MailDtoListDeserializer : Deserializer<List<MailDto>> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())

    override fun deserialize(topic: String?, data: ByteArray?): List<MailDto>? {
        if (data == null) return null

        return try {
            objectMapper.readValue<List<MailDto>>(data)
        } catch (e: Exception) {
            log.error("Errore deserializzazione lista MailDto: ${e.message}", e)
            throw e
        }
    }

    override fun close() {}
}

/**
 * Deserializer per liste di PostaDto
 */
class PostaDtoListDeserializer : Deserializer<List<PostaDto>> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
    override fun deserialize(topic: String?, data: ByteArray?): List<PostaDto>? {
        if (data == null) return null

        return try {
            objectMapper.readValue<List<PostaDto>>(data)
        } catch (e: Exception) {
            log.error("Errore deserializzazione lista PostaDto: ${e.message}", e)
            throw e
        }
    }

    override fun close() {}
}

// ==================== KAFKA CONFIGURATION ====================

@EnableKafka
@EnableKafkaRetryTopic
@Configuration
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    // ==================== PRODUCER ====================

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> =
        DefaultKafkaProducerFactory(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.RETRIES_CONFIG to 3,
                ProducerConfig.BATCH_SIZE_CONFIG to 16384,
                ProducerConfig.LINGER_MS_CONFIG to 10,
                ProducerConfig.COMPRESSION_TYPE_CONFIG to "snappy"
            )
        )

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(producerFactory())

    // ==================== ARCHIVIO CONSUMER (LISTA) ====================

    @Bean
    fun archivioConsumerFactory(): ConsumerFactory<String, List<ArchivioDto>> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-consumer-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ArchivioDtoListDeserializer::class.java,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500,
                ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000,
                ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10000
            )
        )

    @Bean
    fun archivioKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, List<ArchivioDto>> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, List<ArchivioDto>>()
        factory.consumerFactory = archivioConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        factory.containerProperties.pollTimeout = 3000
        return factory
    }

    // ==================== MAIL CONSUMER (LISTA) ====================

    @Bean
    fun mailConsumerFactory(): ConsumerFactory<String, List<MailDto>> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-mail-consumer-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to MailDtoListDeserializer::class.java,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500,
                ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000,
                ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10000
            )
        )

    @Bean
    fun mailKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, List<MailDto>> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, List<MailDto>>()
        factory.consumerFactory = mailConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        factory.containerProperties.pollTimeout = 3000
        return factory
    }

    // ==================== POSTA CONSUMER (LISTA) ====================

    @Bean
    fun postaConsumerFactory(): ConsumerFactory<String, List<PostaDto>> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-posta-consumer-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to PostaDtoListDeserializer::class.java,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500,
                ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000,
                ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10000
            )
        )

    @Bean
    fun postaKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, List<PostaDto>> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, List<PostaDto>>()
        factory.consumerFactory = postaConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        factory.containerProperties.pollTimeout = 3000
        return factory
    }

    // ==================== TASK SCHEDULER PER RETRY ====================

    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 3
        scheduler.setThreadNamePrefix("kafka-retry-")
        scheduler.setAwaitTerminationSeconds(60)
        scheduler.setWaitForTasksToCompleteOnShutdown(true)
        scheduler.initialize()
        return scheduler
    }
}