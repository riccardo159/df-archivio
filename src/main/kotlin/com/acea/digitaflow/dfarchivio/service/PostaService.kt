package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.request.PostaRequest
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.PostaDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PostaService(
    private val notificationProducerService: NotificationProducerService
) {

    private val logger = LoggerFactory.getLogger(PostaService::class.java)

    fun elaboraPosta(poste: List<PostaDto>) {
        logger.info("Elaborazione Posta")

        val mails: List<ArchivioNotificationRequest> = poste.map { posta ->
            ArchivioNotificationRequest(
                tipoCanaleSecondario = "MAIL", // TODO distinguere MAIL / PEC
                flgCanaleSecondario = true,
                tipoDocumento = "MAIL",
                datiDocumento = posta
            )
        }

        notificationProducerService.inviaNotifica(mails)
    }

}
