package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.mapper.NotificationMapper
import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.repository.FatturaRepository
import com.acea.digitaflow.dfarchivio.repository.InfoGenericheRepository
import com.acea.digitaflow.dfarchivio.request.ArchivioRequest
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitalflow.df_common.dto.ArchivioDto
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.dto.PostaDto
import com.acea.digitaflow.dfarchivio.dto.FatturaArchivioDto
import com.acea.digitaflow.dfarchivio.schema.TenantContext
import com.acea.digitalflow.df_common.enums.TipoDocumento
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArchivioService(
    private val documentService: DocumentService,
    private val fatturaService: FatturaService,
    private val fatturaRepository: FatturaRepository,
    private val infoGenericheRepository: InfoGenericheRepository,
    private val notificationMapper: NotificationMapper,
    private val notificationProducerService: NotificationProducerService
) {

    private val logger = LoggerFactory.getLogger(ArchivioService::class.java)

    //Flussi Kafka (documenti)

    fun elaboraArchivio(archivi: List<ArchivioDto>) {
        logger.info("ArchivioService - elaboraArchivio count={}", archivi.size)
        documentService.elaboraArchivio(archivi)
    }

    fun elaboraMail(mails: List<MailDto>) {
        logger.info("ArchivioService - elaboraMail count={}", mails.size)
        documentService.elaboraMail(mails)
    }

    fun elaboraPosta(poste: List<PostaDto>) {
        logger.info("ArchivioService - elaboraPosta count={}", poste.size)
        documentService.elaboraPosta(poste)
    }

    //Flusso fatture SDI (consumer Kafka -> DB -> archiviazione DMS)

    fun elaboraSdi(fatturaArchivioDto: FatturaArchivioDto) {
        logger.info("ArchivioService - elaboraSdi dcsId={}, esitoSdi={}", fatturaArchivioDto.dcsId, fatturaArchivioDto.esitoSdi)

        TenantContext.set(fatturaArchivioDto.societa)
        logger.info("TenantContext impostato per società '{}'", fatturaArchivioDto.societa)

        try {
            val fattura = fatturaRepository.findByInfoGenericheDcsId(fatturaArchivioDto.dcsId)
            if (fattura == null) {
                logger.error("Fattura non trovata nel DB per dcsId={}", fatturaArchivioDto.dcsId)
                throw IllegalStateException("Fattura non trovata per dcsId=${fatturaArchivioDto.dcsId}")
            }

            val infoGeneriche = fattura.infoGeneriche
            val fatturaFile = fattura.fatturaFile

            val archivioRequest = ArchivioRequest(
                dcsId = fatturaArchivioDto.dcsId.toLong(),
                categoriaStampa = resolveCategoriaStampa(fattura.modalitaStampa),
                nomeFileDms = fatturaFile?.nomeFileDms ?: "",
                canaleSecondario = infoGeneriche?.canaleSecondario?.name,
                tipoCanaleSecondario = if (infoGeneriche?.flgCanaleSecondario == true) "SI" else "NO",
                sapDocId = fattura.codiceInvioSap,
                nomeFileEnti = fatturaFile?.nomeFilePa ?: fatturaFile?.nomeFileDms ?: "",
                dmsDestPath = fatturaFile?.dmsDestPath,
                esitoSdi = fatturaArchivioDto.esitoSdi,
                societa = fatturaArchivioDto.societa
            )

            val esito = fatturaService.archiviazione(archivioRequest)

            if (esito.msgErrore.isEmpty()) {
                val notifica = notificationMapper.toNotification(fatturaArchivioDto)
                notificationProducerService.inviaNotifica(listOf(notifica))
                logger.info("Archiviazione SDI completata con successo per dcsId={}", fatturaArchivioDto.dcsId)
            } else {
                logger.warn("Archiviazione SDI con errori per dcsId={}: {}", fatturaArchivioDto.dcsId, esito.msgErrore)
            }
        } finally {
            TenantContext.clear()
        }
    }

    private fun resolveCategoriaStampa(modalitaStampa: String?): String {
        if (modalitaStampa.isNullOrBlank()) return "B2C"

        return when {
            modalitaStampa.contains("PA", ignoreCase = true) -> "PA"
            modalitaStampa.contains("INCO", ignoreCase = true) -> "INCO"
            modalitaStampa.contains("B2B", ignoreCase = true) -> "B2B"
            else -> "B2C"
        }
    }

    //Flusso fatture (archiviazione DMS)

    fun archiviazioneFattura(archivio: ArchivioRequest) {
        logger.info("ArchivioService - archiviazioneFattura dcsId={}", archivio.dcsId)
        fatturaService.archiviazione(archivio)
    }

    //Esito archiviazione dal DMS

    fun registraEsito(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse {
        logger.info("ArchivioService - registraEsito dcsId={}, canale={}", request.dcsId, request.canale)

        TenantContext.set(request.societa)
        logger.info("TenantContext impostato per società '{}'", request.societa)

        try {
            val infoGeneriche = infoGenericheRepository.findByDcsId(request.dcsId)
            if (infoGeneriche == null) {
                val msg = "DCS_ID '${request.dcsId}' non trovato a sistema"
                logger.warn(msg)
                return EsitoArchiviazioneResponse(msgErrore = msg)
            }

            val docType = infoGeneriche.docType
            logger.info("ArchivioService - dcsId={}, docType={}", request.dcsId, docType)

            return when (docType) {
                TipoDocumento.FATTURA -> {
                    logger.info("Instradamento esito verso FatturaService per dcsId={}", request.dcsId)
                    fatturaService.registraEsito(request)
                }
                TipoDocumento.MAIL -> {
                    logger.info("Instradamento esito verso DocumentService (MAIL) per dcsId={}", request.dcsId)
                    documentService.registraEsito(request)
                }
                TipoDocumento.RACCOMANDATE, TipoDocumento.POSTALE -> {
                    logger.info("Instradamento esito verso DocumentService (POSTA) per dcsId={}", request.dcsId)
                    documentService.registraEsito(request)
                }
                TipoDocumento.DOCUMENTI, TipoDocumento.ARCHIVIAZIONE -> {
                    logger.info("Instradamento esito verso DocumentService (ARCHIVIO) per dcsId={}", request.dcsId)
                    documentService.registraEsito(request)
                }
                null -> {
                    val msg = "DocType non valorizzato per DCS_ID '${request.dcsId}'"
                    logger.warn(msg)
                    return EsitoArchiviazioneResponse(msgErrore = msg)
                }
            }
        } finally {
            TenantContext.clear()
        }
    }


}
