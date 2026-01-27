package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.request.ArchivioRequest
import com.acea.digitaflow.dfarchivio.service.ArchivioService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class ArchivioListener(
    private val archivioService: ArchivioService,
) {
    private val logger = LoggerFactory.getLogger(ArchivioListener::class.java)

    @KafkaListener(
        topics = ["archivio-topic"],
        containerFactory = "archivioKafkaListenerContainerFactory",
        groupId = "archivio-consumer-group"
    )
    fun handleArchivio(archivio: ArchivioRequest) {
        logger.info("? Ricevuto messaggio Mail - ID: ${archivio.dcsId}")
        archivioService.archiviazione(archivio)
    }

}