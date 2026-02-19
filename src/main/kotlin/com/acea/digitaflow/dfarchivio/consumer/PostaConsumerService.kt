package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.service.PostaService
import com.acea.digitalflow.df_common.dto.PostaDto
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service

@Service
class PostaConsumerService(private val postaService: PostaService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "false"
    )
    @KafkaListener(
        topics = ["\${kafka.topics.posta}"],
        groupId = "\${kafka.consumer.posta-group-id:archivio-posta-consumer-group}",
        containerFactory = "postaKafkaListenerContainerFactory"
    )
    fun consumePosta(
        @Payload postaDtos: List<PostaDto>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            log.info("📬 Ricevuto batch di ${postaDtos.size} poste [topic=$topic]")
            postaService.elaboraPosta(postaDtos)

            acknowledgment.acknowledge()
            log.info("✓ Batch di ${postaDtos.size} poste completato e acknowledged")

        } catch (e: Exception) {
            log.error("✗ Errore critico processamento batch posta: ${e.message}", e)
            throw e
        }
    }

}