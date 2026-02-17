package com.acea.digitaflow.dfarchivio.producer

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class NotificationProducerService(

    private val kafkaTemplate: KafkaTemplate<String, Any>,

    @Value("\${kafka.notification.topic}")
    private val topicNotification: String
) {

    private val logger = LoggerFactory.getLogger(NotificationProducerService::class.java)

    fun inviaNotifica(payload: Any) {
        logger.info("Invio notifica su topic: {}", topicNotification)
        kafkaTemplate.send(topicNotification, payload)
    }
}
