package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.dto.FileVerificationResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FileVerificationService(
    private val smbService: SmbService
) {

    private val logger = LoggerFactory.getLogger(FileVerificationService::class.java)

    fun verificaFileEsistente(nomeFile: String?, tipoDocumento: String): FileVerificationResult {
        if (nomeFile.isNullOrBlank()) {
            logger.warn("Nome file non specificato per tipoDocumento={}", tipoDocumento)
            return FileVerificationResult(tipoDocumento = tipoDocumento, trovato = false)
        }

        val pathRelativo = nomeFile.trim().replace('\\', '/')

        return try {
            val trovato = smbService.fileExists(pathRelativo)
            logger.info("Verifica file tipoDocumento={}, path={}, trovato={}", tipoDocumento, pathRelativo, trovato)
            FileVerificationResult(tipoDocumento = tipoDocumento, trovato = trovato)
        } catch (e: Exception) {
            logger.error("Errore verifica file path={}: {}", pathRelativo, e.message, e)
            FileVerificationResult(tipoDocumento = tipoDocumento, trovato = false)
        }
    }
}
