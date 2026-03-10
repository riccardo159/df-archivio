package com.acea.digitaflow.dfarchivio.config

import com.acea.digitaflow.dfarchivio.dto.FatturaArchivioDto
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

/**
 * Deserializer per FatturaArchivioDto (singolo oggetto, non lista)
 */
class FatturaArchivioDtoDeserializer : Deserializer<FatturaArchivioDto> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())

    override fun deserialize(topic: String?, data: ByteArray?): FatturaArchivioDto? {
        if (data == null) return null

        return try {
            objectMapper.readValue<FatturaArchivioDto>(data)
        } catch (e: Exception) {
            log.error("Errore deserializzazione FatturaArchivioDto: ${e.message}", e)
            throw e
        }
    }

    override fun close() {}
}

@EnableKafka
@EnableKafkaRetryTopic
@Configuration
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

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

    @Bean
    fun sdiConsumerFactory(): ConsumerFactory<String, FatturaArchivioDto> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "archivio-sdi-consumer-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to FatturaArchivioDtoDeserializer::class.java,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500,
                ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000,
                ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10000
            )
        )

    @Bean
    fun sdiKafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, FatturaArchivioDto> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, FatturaArchivioDto>()
        factory.consumerFactory = sdiConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        factory.containerProperties.pollTimeout = 3000
        return factory
    }

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