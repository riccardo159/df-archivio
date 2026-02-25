package com.acea.digitaflow.dfarchivio.api

import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitaflow.dfarchivio.service.ArchivioService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/archivio")
class ArchivioController(
    private val archivioService: ArchivioService
) {

    private val logger = LoggerFactory.getLogger(ArchivioController::class.java)

    @PostMapping("/esito")
    fun aggiornaStatoDocumento(
        @RequestBody request: EsitoArchiviazioneRequest
    ): ResponseEntity<EsitoArchiviazioneResponse> {
        logger.info("Ricevuta richiesta esito archiviazione DMS per dcsId={}", request.dcsId)

        val response = archivioService.registraEsito(request)

        return if (response.msgErrore.isEmpty()) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
}
