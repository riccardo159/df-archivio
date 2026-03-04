package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.config.StatoDocumento
import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.mapper.NotificationMapper
import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.repository.InfoGenericheRepository
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitaflow.dfarchivio.schema.TenantContext
import com.acea.digitalflow.df_common.dto.ArchivioDto
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.dto.PostaDto
import com.acea.digitalflow.df_common.entity.InfoGeneriche
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DocumentService(
    private val notificationMapper: NotificationMapper,
    private val esitoArchiviazioneService: EsitoArchiviazioneService,
    private val notificationProducerService: NotificationProducerService,
    private val infoGenericheRepository: InfoGenericheRepository
) {

    private val logger = LoggerFactory.getLogger(DocumentService::class.java)

    fun elaboraArchivio(archivi: List<ArchivioDto>) =
        elaboraGenerico(
            items = archivi,
            canale = "ARC",
            dcsExtractor = { it.dcsId },
            societaExtractor = { it.societa },
            mapperCall = { dto, info -> notificationMapper.toNotification(dto, info) }
        )

    fun elaboraMail(mails: List<MailDto>) =
        elaboraGenerico(
            items = mails,
            canale = "EMA",
            dcsExtractor = { it.dcsId },
            societaExtractor = { it.societa },
            mapperCall = { dto, info -> notificationMapper.toNotification(dto, info) }
        )

    fun elaboraPosta(poste: List<PostaDto>) =
        elaboraGenerico(
            items = poste,
            canale = "POS",
            dcsExtractor = { it.dcsId },
            societaExtractor = { it.societa },
            mapperCall = { dto, info -> notificationMapper.toNotification(dto, info) }
        )


    private fun <T> elaboraGenerico(
        items: List<T>,
        canale: String,
        dcsExtractor: (T) -> String,
        societaExtractor: (T) -> String,
        mapperCall: (T, InfoGeneriche) -> ArchivioNotificationRequest
    ) {
        if (items.isEmpty()) {
            logger.info("Elaborazione {} - nessun elemento da processare", canale)
            return
        }

        val itemsBySocieta = items.groupBy(societaExtractor)

        itemsBySocieta.forEach { (societa, groupItems) ->

            TenantContext.set(societa)
            try {
                logger.info("Elaborazione {} per società '{}' - count={}", canale, societa, groupItems.size)

                val notifiche = mutableListOf<ArchivioNotificationRequest>()

                val dcsIds = groupItems
                    .map { dcsExtractor(it) }
                    .distinct()

                val infoByDcsId = infoGenericheRepository
                    .findByDcsIdIn(dcsIds)
                    .associateBy { it.dcsId }

                groupItems.forEach { item ->
                    try {
                        val dcsId = dcsExtractor(item)
                        val info = infoByDcsId[dcsId]

                        if (info == null) {
                            logger.warn("InfoGeneriche non trovata per dcsId={}", dcsId)
                            return@forEach
                        }

                        val esito = registraEsito(dcsId, societa, canale)
                        if (esito.msgErrore.isEmpty()) {
                            notifiche.add(mapperCall(item, info))
                        } else {
                            logger.warn("Esito negativo per dcsId={}: {}", dcsId, esito.msgErrore)
                        }

                    } catch (e: Exception) {
                        logger.error("Errore elaborazione dcsId={}", dcsExtractor(item), e)
                    }
                }

                if (notifiche.isNotEmpty()) {
                    notificationProducerService.inviaNotifica(notifiche)
                }

                logger.info("Elaborazione {} per società '{}' completata - notifiche={}", canale, societa, notifiche.size)

            } finally {
                TenantContext.clear()
            }
        }
    }


    private fun registraEsito(dcsId: String, societa: String, canale: String)
            : EsitoArchiviazioneResponse {
            return esitoArchiviazioneService.aggiornaStato(
                EsitoArchiviazioneRequest(
                    societa = societa,
                    canale = canale,
                    dcsId = dcsId,
                    stato = StatoDocumento.ARCHIVIATO,
                    dataEvento = Instant.now()
                )
            )
    }

    fun registraEsito(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse {
        TenantContext.set(request.societa)
        logger.info("TenantContext impostato per società '{}'", request.societa)
        try {
            return esitoArchiviazioneService.aggiornaStato(request)
        } finally {
            TenantContext.clear()
        }
    }

}