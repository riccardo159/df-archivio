package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.config.StatoDocumento
import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.mapper.NotificationMapper
import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitalflow.df_common.dto.ArchivioDto
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.dto.PostaDto
import com.acea.digitaflow.dfarchivio.schema.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DocumentService(
    private val notificationMapper: NotificationMapper,
    private val esitoArchiviazioneService: EsitoArchiviazioneService,
    private val notificationProducerService: NotificationProducerService
) {

    private val logger = LoggerFactory.getLogger(DocumentService::class.java)

    fun elaboraArchivio(archivi: List<ArchivioDto>) {
        logger.info("Elaborazione Archivio - count={}", archivi.size)

        val notifiche = mutableListOf<ArchivioNotificationRequest>()

        archivi.forEach { archivio ->
            try {
                val esito = registraEsito(archivio.dcsId, archivio.societa, "ARC")

                if (esito.msgErrore.isEmpty()) {
                    notifiche.add(notificationMapper.toNotification(archivio))
                } else {
                    logger.warn("Esito negativo per archivio dcsId={}: {}", archivio.dcsId, esito.msgErrore)
                }
            } catch (e: Exception) {
                logger.error("Errore elaborazione archivio dcsId={}: {}", archivio.dcsId, e.message, e)
            }
        }

        if (notifiche.isNotEmpty()) {
            notificationProducerService.inviaNotifica(notifiche)
        }
    }

    fun elaboraMail(mails: List<MailDto>) {
        logger.info("Elaborazione Mail - count={}", mails.size)

        val notifiche = mutableListOf<ArchivioNotificationRequest>()

        mails.forEach { mail ->
            try {
                val esito = registraEsito(mail.dcsId, mail.societa, "EMA")

                if (esito.msgErrore.isEmpty()) {
                    notifiche.add(notificationMapper.toNotification(mail))
                } else {
                    logger.warn("Esito negativo per mail dcsId={}: {}", mail.dcsId, esito.msgErrore)
                }
            } catch (e: Exception) {
                logger.error("Errore elaborazione mail dcsId={}: {}", mail.dcsId, e.message, e)
            }
        }

        if (notifiche.isNotEmpty()) {
            notificationProducerService.inviaNotifica(notifiche)
        }
    }

    fun elaboraPosta(poste: List<PostaDto>) {
        logger.info("Elaborazione Posta - count={}", poste.size)

        val notifiche = mutableListOf<ArchivioNotificationRequest>()

        poste.forEach { posta ->
            try {
                val esito = registraEsito(posta.dcsId, posta.societa, "POS")

                if (esito.msgErrore.isEmpty()) {
                    notifiche.add(notificationMapper.toNotification(posta))
                } else {
                    logger.warn("Esito negativo per posta dcsId={}: {}", posta.dcsId, esito.msgErrore)
                }
            } catch (e: Exception) {
                logger.error("Errore elaborazione posta dcsId={}: {}", posta.dcsId, e.message, e)
            }
        }

        if (notifiche.isNotEmpty()) {
            notificationProducerService.inviaNotifica(notifiche)
        }
    }

    fun registraEsito(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse {
        TenantContext.set(request.societa)
        logger.info("TenantContext impostato per società '{}'", request.societa)
        return esitoArchiviazioneService.aggiornaStato(request)
    }

    private fun registraEsito(dcsId: String, societa: String, canale: String): EsitoArchiviazioneResponse {
        TenantContext.set(societa)
        logger.info("TenantContext impostato per società '{}'", societa)
        val request = EsitoArchiviazioneRequest(
            societa = societa,
            canale = canale,
            dcsId = dcsId,
            stato = StatoDocumento.ARCHIVIATO,
            dataEvento = Instant.now()
        )
        return esitoArchiviazioneService.aggiornaStato(request)
    }
}
