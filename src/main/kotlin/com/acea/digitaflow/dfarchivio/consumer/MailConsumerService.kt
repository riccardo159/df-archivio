package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.service.ArchivioService
import com.acea.digitalflow.df_common.dto.MailDto
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
class MailConsumerService(private val archivioService: ArchivioService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "false"
    )
    @KafkaListener(
        topics = ["\${kafka.topics.mail}"],
        groupId = "\${kafka.consumer.mail-group-id:archivio-mail-consumer-group}",
        containerFactory = "mailKafkaListenerContainerFactory"
    )
    fun consumeMail(
        @Payload mailDtos: List<MailDto>,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            log.info("Ricevuto batch di {} mail [topic={}]", mailDtos.size, topic)

            archivioService.elaboraMail(mailDtos)

            acknowledgment.acknowledge()
            log.info("Batch di {} mail completato e acknowledged", mailDtos.size)

        } catch (e: Exception) {
            log.error("Errore critico processamento batch mail: {}", e.message, e)
            throw e
        }
    }

}
