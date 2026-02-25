package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.service.ArchivioService
import com.acea.digitalflow.df_common.dto.ArchivioDto
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
class ArchivioConsumerService(private val archivioService: ArchivioService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "false"
    )
    @KafkaListener(
        topics = ["\${kafka.topics.archivio}"],
        groupId = "\${kafka.consumer.group-id:archivio-consumer-group}",
        containerFactory = "archivioKafkaListenerContainerFactory"
    )
    fun consumeArchivio(
        @Payload archivioDtos: List<ArchivioDto>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            log.info("Ricevuto batch di {} archivi [topic={}]", archivioDtos.size, topic)

            archivioService.elaboraArchivio(archivioDtos)

            acknowledgment.acknowledge()
            log.info("Batch di {} archivi completato e acknowledged", archivioDtos.size)

        } catch (e: Exception) {
            log.error("Errore critico processamento batch archivi: {}", e.message, e)
            throw e
        }
    }

}
