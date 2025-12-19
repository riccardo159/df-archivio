package com.acea.digitaflow.dfarchivio.consumer

import com.acea.digitaflow.dfarchivio.request.ArchivioResponse
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class ArchivioListener() {
    private val logger = LoggerFactory.getLogger(ArchivioListener::class.java)

    @KafkaListener(
        topics = ["archivio-topic"],
        containerFactory = "archivioKafkaListenerContainerFactory",
        groupId = "archivio-consumer-group"
    )
    fun handleArchivio(archivio: ArchivioResponse) {
        logger.info("? Ricevuto messaggio Mail - ID: ${archivio.dcsId}")
        if (archivio != null) {
            logger.info("? Ricevuto messaggio Mail - ID: ${archivio.dcsId}")
        }
        processArchivio(archivio)
    }

    private fun processArchivio(archivio: ArchivioResponse) {
        //TODO
    }

}