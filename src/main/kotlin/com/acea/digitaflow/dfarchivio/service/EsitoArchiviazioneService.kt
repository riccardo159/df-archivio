package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.repository.InfoGenericheRepository
import com.acea.digitaflow.dfarchivio.repository.StatoRepository
import com.acea.digitaflow.dfarchivio.repository.TipologiaStatoRepository
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitaflow.dfarchivio.schema.TenantContext
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

    @Transactional
    fun aggiornaStato(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse {
        logger.info(
            "Ricezione esito archiviazione DMS: societa={}, canale={}, dcsId={}, stato={}, dataEvento={}",
            request.societa, request.canale, request.dcsId, request.stato, request.dataEvento
        )

        TenantContext.set(request.societa)
        logger.info("TenantContext impostato per società '{}' -> schema '{}'", request.societa, TenantContext.get())

        // Ricerca documento per dcsId (la societa e' determinata dallo schema multi-tenant)
        val infoGeneriche = infoGenericheRepository.findByDcsId(request.dcsId)
        if (infoGeneriche == null) {
            val msg = "DCS_ID '${request.dcsId}' non trovato a sistema"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(message = msg)
        }

        // Verifica duplicato: stesso dcsId e stesso stato gia' inserito
        val statoDuplicato = statoRepository.findByDcsIdAndTipologiaStatoCodStato(
            request.dcsId, request.stato.toLong()
        )
        if (statoDuplicato.isNotEmpty()) {
            val msg = "Stato ${request.stato} gia' registrato per DCS_ID '${request.dcsId}'"
            logger.warn(msg)
            return EsitoArchiviazioneResponse(message = msg)
        }

        // Recupera la tipologia stato
        val tipologiaStato = tipologiaStatoRepository.findByCodStato(request.stato.toLong())
        if (tipologiaStato == null) {
            val msg = "TipologiaStato con codStato=${request.stato} non trovata in anagrafica"
            logger.error(msg)
            return EsitoArchiviazioneResponse(message = msg)
        }

        // Disattiva gli stati attivi precedenti per questo dcsId
        val statiAttivi = statoRepository.findByDcsIdAndFlagAttivoTrue(request.dcsId)
        if (statiAttivi.isNotEmpty()) {
            logger.info(
                "Disattivazione di {} stati attivi precedenti per DCS_ID '{}'",
                statiAttivi.size, request.dcsId
            )
            statiAttivi.forEach { stato ->
                stato.flagAttivo = false
                statoRepository.save(stato)
            }
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

        infoGeneriche.stato = nuovoStato
        infoGenericheRepository.save(infoGeneriche)

        logger.info(
            "Stato {} salvato e collegato a infoGeneriche per DCS_ID '{}', societa '{}'",
            request.stato, request.dcsId, request.societa
        )

        return EsitoArchiviazioneResponse(success = true, message = "Archiviazione elaborata correttamente")
    }
}
