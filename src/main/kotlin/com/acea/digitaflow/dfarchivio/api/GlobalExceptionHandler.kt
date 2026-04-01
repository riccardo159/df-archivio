package com.acea.digitaflow.dfarchivio.api

import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<EsitoArchiviazioneResponse> {
        logger.warn("Errore di business: {}", ex.reason)
        val response = EsitoArchiviazioneResponse(message = ex.reason ?: ex.message)
        return ResponseEntity.status(ex.statusCode).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<EsitoArchiviazioneResponse> {
        logger.error("Errore interno imprevisto: {}", ex.message, ex)
        val response = EsitoArchiviazioneResponse(message = ex.message ?: "Errore interno imprevisto")
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response)
    }
}
