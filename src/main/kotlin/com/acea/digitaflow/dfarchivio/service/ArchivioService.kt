package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.config.StatoDocumento
import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.dto.FatturaArchivioDto
import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.repository.FatturaRepository
import com.acea.digitaflow.dfarchivio.repository.InfoGenericheRepository
import com.acea.digitaflow.dfarchivio.request.ArchivioRequest
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitaflow.dfarchivio.schema.TenantContext
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.entity.InfoGeneriche
import com.acea.digitalflow.df_common.enums.Canale
import com.acea.digitalflow.df_common.enums.CanaleSecondario
import com.acea.digitalflow.df_common.enums.TipoDocumento
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArchivioService(
    private val documentService: DocumentService,
    private val fatturaService: FatturaService,
    private val fatturaRepository: FatturaRepository,
    private val infoGenericheRepository: InfoGenericheRepository,
    private val notificationProducerService: NotificationProducerService,
    private val mailService: MailService,
    private val entityManager: EntityManager,
    ) {

    private val logger = LoggerFactory.getLogger(ArchivioService::class.java)

    //Flusso fatture SDI (consumer Kafka -> DB -> archiviazione DMS)

    fun elaboraSdi(fatturaArchivioDto: FatturaArchivioDto) {
        logger.info(
            "ArchivioService - elaboraSdi dcsId={}, esitoSdi={}",
            fatturaArchivioDto.dcsId,
            fatturaArchivioDto.esitoSdi
        )
        require(!fatturaArchivioDto.societa.isNullOrBlank()) {
            "societa non presente"
        }
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
                dcsId = fatturaArchivioDto.dcsId,
                categoriaStampa = resolveCategoriaStampa(fattura.modalitaStampa),
                nomeFileDms = fatturaFile?.nomeFileDms ?: "",
                canaleSecondario = infoGeneriche?.canaleSecondario?.name,
                tipoCanaleSecondario = if (infoGeneriche?.flgCanaleSecondario == true) "SI" else "NO",
                sapDocId = fattura.codiceInvioSap,
                nomeFileEnti = fatturaFile?.nomeFilePa ?: fatturaFile?.nomeFileDms ?: "",
                dmsDestPath = fatturaFile?.dmsDestPath,
                esitoSdi = fatturaArchivioDto.esitoSdi,
                societa = fatturaArchivioDto.societa,
                canale = infoGeneriche?.canale
            )
            fatturaService.archiviazione(archivioRequest)
        } finally {
            TenantContext.clear()
        }
    }
//todo modifricare
    //    "modalitaStampa" : "Stampa massiva - Solo archiviazione",
    //    "modalitaStampa" : "Stampa massiva FE",
    private fun resolveCategoriaStampa(modalitaStampa: String?): String {
        if (modalitaStampa.isNullOrBlank()) return "B2C"

        return when {
            modalitaStampa.contains("PA", ignoreCase = true) -> "PA"
            modalitaStampa.contains("INCO", ignoreCase = true) -> "INCO"
            modalitaStampa.contains("B2B", ignoreCase = true) -> "B2B"
            else -> "B2C"
        }
    }

    //Esito archiviazione dal DMS
    @Transactional
    fun registraEsito(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse {
        logger.info("ArchivioService - registraEsito dcsId={}, canale={}", request.dcsId, request.canale)

        logger.info("TenantContext impostato per società '{}'", request.societa)

        val infoGeneriche = infoGenericheRepository.findByDcsId(request.dcsId)
        if (infoGeneriche == null) {
            val msg = "DCS_ID '${request.dcsId}' non trovato a sistema"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(message = msg)
        }

        val docType = infoGeneriche.docType
        logger.info("ArchivioService - dcsId={}, docType={}", request.dcsId, docType)

        return when (docType) {
            TipoDocumento.FATTURA -> {
                logger.info("Instradamento esito verso FatturaService per dcsId={}", request.dcsId)
                val esito = fatturaService.registraEsito(request)
                if (esito.success && request.stato == StatoDocumento.ARCHIVIATO) {
                    inviaNotifica(infoGeneriche, docType, request)
                }
                esito
            }

            TipoDocumento.MAIL -> {
                logger.info("Instradamento esito verso DocumentService (MAIL) per dcsId={}", request.dcsId)
                val esito = documentService.registraEsito(request)
                if (esito.success && request.stato == StatoDocumento.ARCHIVIATO) {
                    inviaNotifica(infoGeneriche, docType, request)
                }
                esito
            }

            TipoDocumento.RACCOMANDATE, TipoDocumento.POSTALE -> {
                logger.info("Instradamento esito verso DocumentService (POSTA) per dcsId={}", request.dcsId)
                val esito = documentService.registraEsito(request)
                if (esito.success && request.stato == StatoDocumento.ARCHIVIATO) {
                    inviaNotifica(infoGeneriche, docType, request)
                }
                esito
            }

            TipoDocumento.DOCUMENTI, TipoDocumento.ARCHIVIAZIONE -> {
                logger.info("Instradamento esito verso DocumentService (ARCHIVIO) per dcsId={}", request.dcsId)
                documentService.registraEsito(request)
            }

            null -> {
                val msg = "DocType non valorizzato per DCS_ID '${request.dcsId}'"
                logger.warn(msg)
                return EsitoArchiviazioneResponse(message = msg)
            }
        }
    }

    @Transactional
    fun inviaNotifica(
        infoGeneriche: InfoGeneriche,
        docType: TipoDocumento,
        request: EsitoArchiviazioneRequest
    ) {
        val canaleSecondario = infoGeneriche.canaleSecondario
            ?: valorizzaCanaleSecondario(request.canale)?.also { nuovoCanale ->
                val fresh = infoGenericheRepository.findByDcsId(infoGeneriche.dcsId)
                    ?: return@also
                fresh.canaleSecondario = nuovoCanale
                infoGenericheRepository.save(fresh)
                logger.info(
                    "Canale secondario {} salvato su DB per dcsId={}",
                    nuovoCanale, infoGeneriche.dcsId
                )
            }

        var datiDocumento = documentService.buildDatiDocumento(request.dcsId, request.societa, docType)

        if (datiDocumento == null &&
            docType == TipoDocumento.FATTURA &&
            canaleSecondario in listOf(CanaleSecondario.MAIL, CanaleSecondario.PEC)
        ) {
            datiDocumento = mailService.buildDto(request.dcsId, request.societa)
            logger.info("MailDto costruito per FATTURA con canale {} dcsId={}", canaleSecondario, request.dcsId)
        }

        val notifica = ArchivioNotificationRequest(
            tipoCanaleSecondario = canaleSecondario,
            flgCanaleSecondario = infoGeneriche.flgCanaleSecondario ?: false,
            tipoDocumento = docType,
            datiDocumento = datiDocumento,
            societa = request.societa,
            dcsId = request.dcsId,
            testo = infoGeneriche.mail?.testo,
            campoDifferito = infoGeneriche.differito?.toString()
        )
        notificationProducerService.inviaNotifica(listOf(notifica))
        logger.info(
            "Notifica inviata per {} archiviato dcsId={}, canaleSecondario={}",
            docType,
            request.dcsId,
            canaleSecondario
        )
    }

    private fun valorizzaCanaleSecondario(canale: Canale?): CanaleSecondario? {
        return when (canale) {
            Canale.EMA -> CanaleSecondario.MAIL
            Canale.PEC -> CanaleSecondario.PEC
            Canale.RAC, Canale.POS, Canale.FAX -> CanaleSecondario.STAMPATORE
            else -> null
        }
    }

}