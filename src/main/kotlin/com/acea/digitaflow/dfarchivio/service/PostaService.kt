package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.PostaDto
import com.acea.digitalflow.df_common.enums.CanaleSecondario
import com.acea.digitalflow.df_common.enums.TipoDocumento
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
                tipoCanaleSecondario = CanaleSecondario.POSTA, // TODO distinguere MAIL / PEC
                flgCanaleSecondario = true,
                tipoDocumento = TipoDocumento.POSTALE,
                datiDocumento = posta,
                societa = posta.societa,
                dcsId = posta.dcsId
            )
        }

        notificationProducerService.inviaNotifica(mails)
    }

}
