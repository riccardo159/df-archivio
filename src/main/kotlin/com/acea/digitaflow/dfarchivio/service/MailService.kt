package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.request.MailRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MailService(
    private val notificationProducerService: NotificationProducerService
) {

    private val logger = LoggerFactory.getLogger(MailService::class.java)

    fun elaboraMail(mail: MailRequest) {
        logger.info("Elaborazione Mail - dcsId: {}", mail.dcsId)
        notificationProducerService.inviaNotifica(mail)
    }
}
