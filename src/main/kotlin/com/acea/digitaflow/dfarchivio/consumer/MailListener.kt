package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.request.MailRequest
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class MailListener {

    private val logger = LoggerFactory.getLogger(MailListener::class.java)

    @KafkaListener(
        topics = ["\${kafka.topics.mail:mail-topic}"],
        containerFactory = "mailKafkaListenerContainerFactory",
        groupId = "archivio-mail-consumer-group"
    )
    fun handleMail(mail: MailRequest) {
        logger.info("Ricevuto messaggio Mail - dcsId: ${mail.dcsId}")
    }
}
