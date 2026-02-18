package com.acea.digitaflow.dfarchivio.service


import com.acea.digitaflow.dfarchivio.config.SmbProperties
import com.acea.digitaflow.dfarchivio.dto.FileVerificationResult
import com.acea.digitaflow.dfarchivio.dto.TipoErroreFile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FileVerificationService(
    private val smbService: SmbService,
    private val configProvider: SmbConfigService

) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Verifica se un file esiste sulla share SMB, dato il nome file e il tipo documento.
     */
    fun verificaFileEsistente(nomeFile: String?, tipoDocumento: String): FileVerificationResult {
        val props = configProvider.getEffectiveConfig("DEFAULT")

        if (nomeFile.isNullOrBlank()) {
            return FileVerificationResult(
                tipoDocumento = tipoDocumento,
                nomeFile = null,
                pathCompleto = "",
                trovato = false,
                messaggioErrore = "Nome file non specificato",
                tipoErrore = TipoErroreFile.FILE_NON_TROVATO
            )
        }

        return try {
            val pathCompleto = nomeFile.trim()

            // Costruisci il root della share
            // Es: "\\zenit.aceaspa.it\Idrico_collaudo_DMS_DATI$"
            val shareRoot = "\\\\${props.server}\\${props.share.trim('\\')}"

            // Rimuovi il prefisso della share per ottenere il path relativo
            // Es: "\\zenit.aceaspa.it\Idrico_collaudo_DMS_DATI$\AAT2_OUTBOUND\FLUSSI_STAMPATORE\file.pdf"
            //  -> "AAT2_OUTBOUND\FLUSSI_STAMPATORE\file.pdf"
            val pathRelativo = pathCompleto
                .removePrefix(shareRoot)
                .trimStart('\\')
                .replace('\\', '/')  // Normalizza i separatori per SMB

            // Estrai il nome del file
            val lastSep = pathCompleto.lastIndexOf('\\')
            val fileName = if (lastSep == -1) {
                pathCompleto
            } else {
                pathCompleto.substring(lastSep + 1)
            }

            log.debug("Verifica esistenza file SMB: pathCompleto=$pathCompleto, pathRelativo=$pathRelativo, fileName=$fileName")

            // Usa withShare per ottenere il DiskShare e verifica l'esistenza
            val exists = smbService.withShare { share ->
                smbService.fileExists(share, pathRelativo)
            }

            if (!exists) {
                log.debug("File non trovato su SMB: $pathCompleto")
                FileVerificationResult(
                    tipoDocumento = tipoDocumento,
                    nomeFile = fileName,
                    pathCompleto = pathCompleto,
                    trovato = false,
                    messaggioErrore = "File non trovato: $pathCompleto",
                    tipoErrore = TipoErroreFile.FILE_NON_TROVATO
                )
            } else {
                log.debug("File trovato su SMB: $pathCompleto")
                FileVerificationResult(
                    tipoDocumento = tipoDocumento,
                    nomeFile = fileName,
                    pathCompleto = pathCompleto,
                    trovato = true,
                    messaggioErrore = null,
                    tipoErrore = null
                )
            }

        } catch (e: SecurityException) {
            log.error("Errore di sicurezza durante verifica file SMB: ${e.message}", e)
            FileVerificationResult(
                tipoDocumento = tipoDocumento,
                nomeFile = nomeFile,
                pathCompleto = "",
                trovato = false,
                messaggioErrore = "Errore di sicurezza: ${e.message}",
                tipoErrore = TipoErroreFile.PERMISSION_DENIED
            )
        } catch (e: Exception) {
            log.error("Errore durante verifica file SMB: ${e.message}", e)
            FileVerificationResult(
                tipoDocumento = tipoDocumento,
                nomeFile = nomeFile,
                pathCompleto = "",
                trovato = false,
                messaggioErrore = "Errore I/O SMB: ${e.message}",
                tipoErrore = TipoErroreFile.ERRORE_IO
            )
        }
    }
    /**
     * Utility per costruire un path leggibile (relativo alla share SMB).
     */
    private fun buildSmbPath(relativeDir: String?, fileName: String): String =
        if (relativeDir.isNullOrBlank()) fileName
        else relativeDir.trimEnd('/') + "/" + fileName
}