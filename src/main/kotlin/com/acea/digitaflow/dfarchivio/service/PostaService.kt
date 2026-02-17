package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.request.PostaRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PostaService(
    private val notificationProducerService: NotificationProducerService
) {

    private val logger = LoggerFactory.getLogger(PostaService::class.java)

    fun elaboraPosta(posta: PostaRequest) {
        logger.info("Elaborazione Posta - dcsId: {}", posta.dcsId)
        notificationProducerService.inviaNotifica(posta)
    }
}
