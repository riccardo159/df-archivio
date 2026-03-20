package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitaflow.dfarchivio.schema.TenantContext
import com.acea.digitalflow.df_common.enums.TipoDocumento
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DocumentService(
    private val esitoArchiviazioneService: EsitoArchiviazioneService,
    private val mailService: MailService,
    private val postaService: PostaService,
    private val archivioDocService: ArchivioDocService
) {

    private val logger = LoggerFactory.getLogger(DocumentService::class.java)

    fun registraEsito(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse {
        TenantContext.set(request.societa)
        logger.info("TenantContext impostato per società '{}'", request.societa)
        try {
            return esitoArchiviazioneService.aggiornaStato(request)
        } finally {
            TenantContext.clear()
        }
    }

    fun buildDatiDocumento(dcsId: String, societa: String, docType: TipoDocumento): Any? {
        return when (docType) {
            TipoDocumento.MAIL -> mailService.buildDto(dcsId, societa)

            TipoDocumento.RACCOMANDATE, TipoDocumento.POSTALE -> postaService.buildDto(dcsId, societa)

            TipoDocumento.DOCUMENTI, TipoDocumento.ARCHIVIAZIONE -> archivioDocService.buildDto(dcsId, societa)

            TipoDocumento.FATTURA -> {
                // Per le fatture il canale secondario è STAMPATORE,
                // che non usa datiDocumento ma fa query DB propria in df-notification
                null
            }

            else -> {
                logger.warn("DocType {} non gestito per invio notifica, dcsId={}", docType, dcsId)
                null
            }
        }
    }

}
