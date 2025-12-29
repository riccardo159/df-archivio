package com.acea.digitaflow.dfarchivio.service


import com.acea.digitaflow.dfarchivio.SmbProperties
import com.acea.digitaflow.dfarchivio.dto.FileVerificationResult
import com.acea.digitaflow.dfarchivio.dto.TipoErroreFile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FileVerificationService(
    private val smbService: SmbService,
    private val props: SmbProperties

) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Verifica se un file esiste sulla share SMB, dato il nome file e il tipo documento.
     */
    fun verificaFileEsistente(nomeFile: String?, tipoDocumento: String): FileVerificationResult {

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

            // cerca l'ultimo separatore "\" (UNC)
            val lastSep = pathCompleto.lastIndexOf('\\')

            val (relativeDir, fileName) =
                if (lastSep == -1) {
                    // nessuna directory, solo nome
                    null to pathCompleto
                } else {
                    val dir = pathCompleto.substring(0, lastSep)
                    val name = pathCompleto.substring(lastSep + 1)
                    dir to name
                }
// stringa runtime:  \\zenit.aceaspa.it\Idrico_collaudo_DMS_DATI$
            val shareRoot = "\\\\${props.server}\\${props.share.trim('\\')}"

// absoluteDir = "\\zenit.aceaspa.it\Idrico_collaudo_DMS_DATI$\AAT2_OUTBOUND\FLUSSI_STAMPATORE\COMPUNET"
            val pathRelativo = pathCompleto
                ?.removePrefix(shareRoot)  // -> "\AAT2_OUTBOUND\FLUSSI_STAMPATORE\COMPUNET"
                ?.trimStart('\\')          // -> "AAT2_OUTBOUND\FLUSSI_STAMPATORE\COMPUNET"


            log.debug("Verifica esistenza file SMB: $pathCompleto (dir=$relativeDir, file=$fileName)")

            val exists: Boolean = pathRelativo
                ?.let { smbService.fileExists(it) }
                ?: false

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