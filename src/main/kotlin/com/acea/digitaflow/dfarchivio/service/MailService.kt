package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.enums.CanaleSecondario
import com.acea.digitalflow.df_common.enums.TipoDocumento
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MailService(
    private val notificationProducerService: NotificationProducerService
) {

    private val logger = LoggerFactory.getLogger(MailService::class.java)

    fun elaboraMail(mails: List<MailDto>) {
        logger.info("Elaborazione Mail - count={}", mails.size)

        val requests = mails.map { mail ->
            ArchivioNotificationRequest(
                tipoCanaleSecondario = CanaleSecondario.MAIL,// TODO distinguere MAIL / PEC
                flgCanaleSecondario = true,
                tipoDocumento = TipoDocumento.MAIL,
                datiDocumento = mail,
                societa = mail.societa,
                dcsId = mail.dcsId,
            )
        }

        notificationProducerService.inviaNotifica(requests)
    }

}

