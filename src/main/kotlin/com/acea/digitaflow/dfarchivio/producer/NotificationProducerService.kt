package com.acea.digitaflow.dfarchivio.producer

import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class NotificationProducerService(

    private val kafkaTemplate: KafkaTemplate<String, Any>,

    @Value("\${kafka.topics.notification}")
    private val topicNotification: String
) {

    private val logger = LoggerFactory.getLogger(NotificationProducerService::class.java)

    fun inviaNotifica(payload: List<ArchivioNotificationRequest>) {
        logger.info("Invio notifica su topic: {}", topicNotification)
        kafkaTemplate.send(topicNotification, payload)
    }

}
