package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.config.StatoDocumento
import com.acea.digitaflow.dfarchivio.dto.EsitoArchiviazioneResponse
import com.acea.digitaflow.dfarchivio.request.ArchivioRequest
import com.acea.digitaflow.dfarchivio.request.EsitoArchiviazioneRequest
import com.acea.digitaflow.dfarchivio.service.storage.FileStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class FatturaService(
    private val fileStorageService: FileStorageService,
    private val esitoArchiviazioneService: EsitoArchiviazioneService
) {

    private val logger = LoggerFactory.getLogger(FatturaService::class.java)

    private fun sapDocIdDestPath(societa: String): String =
        fileStorageService.buildPath(societa + "_OUTBOUND/MASSIVI_OUTBOUND_SAPDOCID")

    private fun noSapDocIdDestPath(societa: String): String =
        fileStorageService.buildPath(societa + "_OUTBOUND/MASSIVI_OUTBOUND_NO_SAPDOCID")

    private fun bckDataDestPath(societa: String): String =
        fileStorageService.buildPath(societa + "_OUTBOUND/BCK_DATA")

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
                // Punto 1: B2C, esito 31(NS) -> Sposta file DMS in backup
                archivio.categoriaStampa == "B2C" &&
                        archivio.esitoSdi == 31 -> {
                    logger.info("Caso IDRICO Punto 1: spostamento file DMS in backup")
                    spostaFileDmsInBackup(archivio)
                }

                // Punto 2: B2C, canaleSecondario = SI, esito 30(RC) o 32(MC)
                archivio.categoriaStampa == "B2C" &&
                        archivio.tipoCanaleSecondario == "SI" &&
                        (archivio.esitoSdi == 30 || archivio.esitoSdi == 32) -> {
                    logger.info("Caso IDRICO Punto 2: spostamento file DMS")
                    spostaFileDms(archivio)
                }

                // Punto 3: B2B, canaleSecondario = NO, esito 32(MC)
                archivio.categoriaStampa == "B2B" &&
                        archivio.tipoCanaleSecondario == "NO" &&
                        archivio.esitoSdi == 32 -> {
                    logger.info("Caso IDRICO Punto 3: spostamento file DMS")
                    spostaFileDms(archivio)
                }

                // Punto 4: B2B, canaleSecondario = NO, esito 30(RC)
                archivio.categoriaStampa == "B2B" &&
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
            canale = archivio.canale,
            dcsId = archivio.dcsId.toString(),
            stato = stato,
            dataEvento = Instant.now()
        )
        return esitoArchiviazioneService.aggiornaStato(request)
    }

    private fun risolviDestinazioneDms(archivio: ArchivioRequest): String {
        archivio.dmsDestPath?.let { return fileStorageService.buildPath(it) }

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

        val pdfOk = moveFile(sourceDir, "$fileBaseName.pdf", destDir, overwrite = false)
        val xmlOk = moveFile(sourceDir, "$fileBaseName.xml", destDir, overwrite = false)

        logger.info("Spostamento file DMS completato per base={}, pdfOk={}, xmlOk={}", fileBaseName, pdfOk, xmlOk)
        return pdfOk && xmlOk
    }

    private fun spostaFileDmsInBackup(archivio: ArchivioRequest): Boolean {
        val sourceBase = archivio.nomeFileDms
        val destDir = bckDataDestPath(archivio.societa)

        val lastSlash = sourceBase.lastIndexOf('/')
        val sourceDir = if (lastSlash >= 0) sourceBase.substring(0, lastSlash) else null
        val fileBaseName = if (lastSlash >= 0) sourceBase.substring(lastSlash + 1) else sourceBase

        val pdfOk = moveFile(sourceDir, "$fileBaseName.pdf", destDir, overwrite = true)
        val xmlOk = moveFile(sourceDir, "$fileBaseName.xml", destDir, overwrite = true)

        logger.info(
            "Spostamento file DMS in backup completato per base={}, pdfOk={}, xmlOk={}",
            fileBaseName, pdfOk, xmlOk
        )
        return pdfOk && xmlOk
    }

    private fun moveFile(
        sourceRelativeDir: String?,
        sourceFileName: String,
        destRelativeDir: String?,
        overwrite: Boolean
    ): Boolean {

        val sourcePath = listOfNotNull(sourceRelativeDir, sourceFileName)
            .joinToString("/")

        val destPath = listOfNotNull(destRelativeDir, sourceFileName)
            .joinToString("/")

        logger.info("Spostamento file (local): {} -> {}", sourcePath, destPath)

        return fileStorageService.moveFile(sourcePath, destPath, overwrite)
    }
}
