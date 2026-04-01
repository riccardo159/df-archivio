package com.acea.digitaflow.dfarchivio.config

import com.acea.digitaflow.dfarchivio.dto.FatturaArchivioDto
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.admin.NewTopic
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
import org.springframework.kafka.config.TopicBuilder
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
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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
