package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.config.StatoDocumento
import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.request.ArchivioRequest
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.EnumSet

@Service
class FatturaService(
    private val smbService: SmbService,
    private val esitoArchiviazioneService: EsitoArchiviazioneService
) {

    private val logger = LoggerFactory.getLogger(FatturaService::class.java)

    private fun sapDocIdDestPath(societa: String): String =
        smbService.buildPath(societa + "_OUTBOUND/MASSIVI_OUTBOUND_SAPDOCID")

    private fun noSapDocIdDestPath(societa: String): String =
        smbService.buildPath(societa + "_OUTBOUND/MASSIVI_OUTBOUND_NO_SAPDOCID")

    private fun bckDataDestPath(societa: String): String =
        smbService.buildPath(societa + "_OUTBOUND/BCK_DATA")

    fun archiviazione(archivio: ArchivioRequest): EsitoArchiviazioneResponse {
        logger.info(
            "Avvio archiviazione DMS per dcsId={}, categoria={}, esito={}, canaleSecondario={}",
            archivio.dcsId, archivio.categoriaStampa, archivio.esitoSdi, archivio.canaleSecondario
        )

        // 1. Esegui operazione file DMS
        val operazioneOk = eseguiOperazioneFile(archivio)

        // 2. Registra esito su DB
        val statoEsito = if (operazioneOk) StatoDocumento.ARCHIVIATO else StatoDocumento.NON_ARCHIVIATO
        val esito = registraEsito(archivio, statoEsito)

        return esito
    }

    fun registraEsito(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse =
        esitoArchiviazioneService.aggiornaStato(request)

    private fun eseguiOperazioneFile(archivio: ArchivioRequest): Boolean {
        return try {
            when {
                // Punto 1: B2C / B2C, esito 31(NS) -> Sposta file DMS in backup
                archivio.categoriaStampa == "B2C" &&
                        archivio.canaleSecondario == "B2C" &&
                        archivio.esitoSdi == 31 -> {
                    logger.info("Caso IDRICO Punto 1: spostamento file DMS in backup")
                    spostaFileDmsInBackup(archivio)
                }

                // Punto 2: B2C / B2C, canaleSecondario = SI, esito 30(RC) o 32(MC)
                archivio.categoriaStampa == "B2C" &&
                        archivio.canaleSecondario == "B2C" &&
                        archivio.tipoCanaleSecondario == "SI" &&
                        (archivio.esitoSdi == 30 || archivio.esitoSdi == 32) -> {
                    logger.info("Caso IDRICO Punto 2: spostamento file DMS")
                    spostaFileDms(archivio)
                }

                // Punto 3: B2B / B2C, canaleSecondario = NO, esito 32(MC)
                archivio.categoriaStampa == "B2B" &&
                        archivio.canaleSecondario == "B2C" &&
                        archivio.tipoCanaleSecondario == "NO" &&
                        archivio.esitoSdi == 32 -> {
                    logger.info("Caso IDRICO Punto 3: spostamento file DMS")
                    spostaFileDms(archivio)
                }

                // Punto 4: B2B / B2C, canaleSecondario = NO, esito 30(RC)
                archivio.categoriaStampa == "B2B" &&
                        archivio.canaleSecondario == "B2C" &&
                        archivio.tipoCanaleSecondario == "NO" &&
                        archivio.esitoSdi == 30 -> {
                    logger.info("Caso IDRICO Punto 4: spostamento file DMS")
                    spostaFileDms(archivio)
                }

                // Punto 5: PA / INCO, esito 31(NS) o 39(CK) -> Sposta file DMS in backup
                archivio.categoriaStampa in listOf("PA", "INCO") &&
                        (archivio.esitoSdi == 31 || archivio.esitoSdi == 39) -> {
                    logger.info("Caso IDRICO Punto 5: spostamento file DMS in backup")
                    spostaFileDmsInBackup(archivio)
                }

                // Punto 6: PA / INCO, esito 30(RC) o 32(MC)
                archivio.categoriaStampa in listOf("PA", "INCO") &&
                        (archivio.esitoSdi == 30 || archivio.esitoSdi == 32) -> {
                    logger.info("Caso IDRICO Punto 6: spostamento file DMS")
                    spostaFileDms(archivio)
                }

                else -> {
                    logger.warn(
                        "Nessuna azione prevista per combinazione categoria={}, canaleSecondario={}, tipoCanaleSecondario={}, esito={}",
                        archivio.categoriaStampa, archivio.canaleSecondario,
                        archivio.tipoCanaleSecondario, archivio.esitoSdi
                    )
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Errore operazione file DMS per dcsId={}: {}", archivio.dcsId, e.message, e)
            false
        }
    }

    private fun registraEsito(archivio: ArchivioRequest, stato: Int): EsitoArchiviazioneResponse {
        val request = EsitoArchiviazioneRequest(
            societa = archivio.societa,
            canale = "STA",
            dcsId = archivio.dcsId.toString(),
            stato = stato,
            dataEvento = Instant.now()
        )
        return esitoArchiviazioneService.aggiornaStato(request)
    }

    private fun risolviDestinazioneDms(archivio: ArchivioRequest): String {
        archivio.dmsDestPath?.let { return smbService.buildPath(it) }

        return if (!archivio.sapDocId.isNullOrBlank()) {
            sapDocIdDestPath(archivio.societa)
        } else {
            noSapDocIdDestPath(archivio.societa)
        }
    }

    private fun spostaFileDms(archivio: ArchivioRequest): Boolean {
        val sourceBase = archivio.nomeFileDms
        val destDir = risolviDestinazioneDms(archivio)

        val lastSlash = sourceBase.lastIndexOf('/')
        val sourceDir = if (lastSlash >= 0) sourceBase.substring(0, lastSlash) else null
        val fileBaseName = if (lastSlash >= 0) sourceBase.substring(lastSlash + 1) else sourceBase

        val pdfOk = moveFile(sourceDir, "$fileBaseName.pdf", destDir, sovrascrivi = false)
        val xmlOk = moveFile(sourceDir, "$fileBaseName.xml", destDir, sovrascrivi = false)

        logger.info("Spostamento file DMS completato per base={}, pdfOk={}, xmlOk={}", fileBaseName, pdfOk, xmlOk)
        return pdfOk && xmlOk
    }

    private fun spostaFileDmsInBackup(archivio: ArchivioRequest): Boolean {
        val sourceBase = archivio.nomeFileDms
        val destDir = bckDataDestPath(archivio.societa)

        val lastSlash = sourceBase.lastIndexOf('/')
        val sourceDir = if (lastSlash >= 0) sourceBase.substring(0, lastSlash) else null
        val fileBaseName = if (lastSlash >= 0) sourceBase.substring(lastSlash + 1) else sourceBase

        val pdfOk = moveFile(sourceDir, "$fileBaseName.pdf", destDir, sovrascrivi = true)
        val xmlOk = moveFile(sourceDir, "$fileBaseName.xml", destDir, sovrascrivi = true)

        logger.info("Spostamento file DMS in backup completato per base={}, pdfOk={}, xmlOk={}", fileBaseName, pdfOk, xmlOk)
        return pdfOk && xmlOk
    }

    private fun moveFile(sourceRelativeDir: String?, sourceFileName: String, destRelativeDir: String?, sovrascrivi: Boolean): Boolean {
        val sourceDir = smbService.buildPath(sourceRelativeDir)
        val destDir = smbService.buildPath(destRelativeDir)
        val sourcePath = "$sourceDir/$sourceFileName"
        val destPath = "$destDir/$sourceFileName"

        logger.info("Spostamento file SMB: {} -> {}", sourcePath, destPath)

        return smbService.withShare { share ->
            try {
                val sourceFile = share.openFile(
                    sourcePath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    EnumSet.noneOf(FileAttributes::class.java),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    EnumSet.noneOf(SMB2CreateOptions::class.java)
                )

                val destFile = share.openFile(
                    destPath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                )

                sourceFile.use { src ->
                    destFile.use { dst ->
                        src.inputStream.use { input ->
                            dst.outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                if (sovrascrivi) {
                    share.rm(sourcePath)
                }

                logger.info("File spostato con successo: {} -> {}", sourcePath, destPath)
                true
            } catch (e: Exception) {
                logger.error("Errore spostamento file {} -> {}", sourcePath, destPath, e)
                false
            }
        }
    }
}
