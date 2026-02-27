package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.repository.InfoGenericheRepository
import com.acea.digitaflow.dfarchivio.repository.StatoRepository
import com.acea.digitaflow.dfarchivio.repository.TipologiaStatoRepository
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitalflow.df_common.entity.Stato
import com.acea.digitalflow.df_common.enums.SistemaCoinvolto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EsitoArchiviazioneService(
    private val infoGenericheRepository: InfoGenericheRepository,
    private val tipologiaStatoRepository: TipologiaStatoRepository,
    private val statoRepository: StatoRepository
) {

    private val logger = LoggerFactory.getLogger(EsitoArchiviazioneService::class.java)

    companion object {
        val SOCIETA_VALIDE = setOf(
            "ATO2", "AAT5", "ACQU", "GORI", "GESE", "PUBL", "FIOR", "CGST", "UMBR"
        )

        val CANALI_VALIDI = setOf(
            "POS", "PEC", "RAC", "EMA", "FAX", "ARC", "STA"
        )

        val STATI_VALIDI = setOf(
            1000, 1010, 1020, 1030, 1040, 1050, 1060, 1070, 1080
        )
    }

    @Transactional
    fun aggiornaStato(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse {
        logger.info(
            "Ricezione esito archiviazione DMS: societa={}, canale={}, dcsId={}, stato={}, dataEvento={}",
            request.societa, request.canale, request.dcsId, request.stato, request.dataEvento
        )

        // Validazione societa
        if (request.societa.uppercase() !in SOCIETA_VALIDE) {
            val msg = "Societa '${request.societa}' non valida. Valori ammessi: $SOCIETA_VALIDE"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(msgErrore = msg)
        }

        // Validazione canale
        if (request.canale !in CANALI_VALIDI) {
            val msg = "Canale '${request.canale}' non tra quelli consentiti. Valori ammessi: $CANALI_VALIDI"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(msgErrore = msg)
        }

        // Validazione stato
        if (request.stato !in STATI_VALIDI) {
            val msg = "Stato '${request.stato}' non tra quelli consentiti. Valori ammessi: $STATI_VALIDI"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(msgErrore = msg)
        }

        // Ricerca documento per dcsId (la societa e' determinata dallo schema multi-tenant)
        val infoGeneriche = infoGenericheRepository.findByDcsId(request.dcsId)
        if (infoGeneriche == null) {
            val msg = "DCS_ID '${request.dcsId}' non trovato a sistema"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(msgErrore = msg)
        }

        // Verifica duplicato: stesso dcsId e stesso stato gia' inserito
        val statoDuplicato = statoRepository.findByDcsIdAndTipologiaStatoCodStato(
            request.dcsId, request.stato.toLong()
        )
        if (statoDuplicato != null) {
            val msg = "Stato ${request.stato} gia' registrato per DCS_ID '${request.dcsId}'"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(msgErrore = msg)
        }

        // Recupera la tipologia stato
        val tipologiaStato = tipologiaStatoRepository.findByCodStato(request.stato.toLong())
        if (tipologiaStato == null) {
            val msg = "TipologiaStato con codStato=${request.stato} non trovata in anagrafica"
            logger.error(msg)
            return EsitoArchiviazioneResponse(msgErrore = msg)
        }

        // Salvataggio nuovo stato
        val nuovoStato = Stato(
            dcsId = request.dcsId,
            tipologiaStato = tipologiaStato,
            dataOraIns = request.dataEvento,
            sysOwner = SistemaCoinvolto.DCS,
            flagAttivo = true
        )

        statoRepository.save(nuovoStato)

        logger.info(
            "Stato {} salvato con successo per DCS_ID '{}', societa '{}'",
            request.stato, request.dcsId, request.societa
        )

        return EsitoArchiviazioneResponse()
    }
}
