package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.request.PostaRequest
import com.acea.digitaflow.dfarchivio.service.PostaService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PostaListener(
    private val postaService: PostaService
) {

    private val logger = LoggerFactory.getLogger(PostaListener::class.java)

    @KafkaListener(
        topics = ["\${kafka.topics.posta:posta-topic}"],
        containerFactory = "postaKafkaListenerContainerFactory",
        groupId = "archivio-posta-consumer-group"
    )
    fun handlePosta(posta: PostaRequest) {
        logger.info("Ricevuto messaggio Posta - dcsId: ${posta.dcsId}")
        postaService.elaboraPosta(posta)
    }
}
