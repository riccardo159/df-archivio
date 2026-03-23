package com.acea.digitaflow.dfarchivio.api

import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitaflow.dfarchivio.schema.TenantContext
import com.acea.digitaflow.dfarchivio.service.ArchivioService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/aceaidrico/docsflow/v1/archivio")
class ArchivioController(
    private val archivioService: ArchivioService
) {

    private val logger = LoggerFactory.getLogger(ArchivioController::class.java)

    @PostMapping("/esito")
    fun aggiornaStatoDocumento(
        @RequestBody request: EsitoArchiviazioneRequest
    ): ResponseEntity<EsitoArchiviazioneResponse> {
        logger.info("Ricevuta richiesta esito archiviazione DMS per dcsId={}", request.dcsId)
        TenantContext.set(request.societa)
        val response = archivioService.registraEsito(request)
        TenantContext.clear()
        return if (response.msgErrore.isEmpty()) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
}
