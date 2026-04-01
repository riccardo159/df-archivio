package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.dto.FatturaArchivioDto
import com.acea.digitaflow.dfarchivio.service.ArchivioService
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
class FatturaConsumerService(private val archivioService: ArchivioService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "true",
        exclude = [IllegalArgumentException::class]
    )
    @KafkaListener(
        topics = ["\${kafka.topics.sdi}"],
        groupId = "\${kafka.consumer.sdi-group-id:archivio-sdi-consumer-group}",
        containerFactory = "sdiKafkaListenerContainerFactory"
    )
    fun consumeSdi(
        @Payload fatturaArchivioDto: FatturaArchivioDto,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            log.info("Ricevuta fattura SDI dcsId={} [topic={}]", fatturaArchivioDto.dcsId, topic)

            archivioService.elaboraSdi(fatturaArchivioDto)

            acknowledgment.acknowledge()
            log.info("Fattura SDI dcsId={} elaborata e acknowledged", fatturaArchivioDto.dcsId)

        } catch (e: Exception) {
            log.error("Errore critico processamento fattura SDI dcsId={}: {}", fatturaArchivioDto.dcsId, e.message, e)
            throw e
        }
    }
}
