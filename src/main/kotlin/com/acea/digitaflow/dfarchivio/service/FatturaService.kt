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

    companion object {
        private const val OUTBOUND_SUFFIX = "_OUTBOUND"
        private const val MASSIVI_SAPDOCID = "MASSIVI_OUTBOUND_SAPDOCID"
        private const val MASSIVI_NO_SAPDOCID = "MASSIVI_OUTBOUND_NO_SAPDOCID"
        private const val BCK_DATA = "BCK_DATA"

        private val CATEGORIE_PA_INCO = setOf("PA", "INCO")
    }

    // ---- Path di destinazione ----

    private fun outboundDir(societa: String): String =
        "${societa}$OUTBOUND_SUFFIX"

    private fun sapDocIdDestPath(societa: String): String =
        fileStorageService.combinePath(outboundDir(societa), MASSIVI_SAPDOCID)

    private fun noSapDocIdDestPath(societa: String): String =
        fileStorageService.combinePath(outboundDir(societa), MASSIVI_NO_SAPDOCID)

    private fun bckDataDestPath(societa: String): String =
        fileStorageService.combinePath(outboundDir(societa), BCK_DATA)

    // ---- Archiviazione ----

    fun archiviazione(archivio: ArchivioRequest) {
        logger.info(
            "Avvio archiviazione DMS per dcsId={}, categoria={}, esito={}, canaleSecondario={}",
            archivio.dcsId, archivio.categoriaStampa, archivio.esitoSdi, archivio.canaleSecondario
        )

        val operazioneOk = eseguiOperazioneFile(archivio)

        // // 2. Registra esito su DB
        // val statoEsito = if (operazioneOk) StatoDocumento.ARCHIVIATO else StatoDocumento.NON_ARCHIVIATO
        // val esito = registraEsito(archivio, statoEsito)
    }

    fun registraEsito(request: EsitoArchiviazioneRequest): EsitoArchiviazioneResponse =
        esitoArchiviazioneService.aggiornaStato(request)

    /**
     * Determina l'azione da eseguire in base a categoriaStampa, canaleSecondario ed esitoSdi.
     *
     * Regole:
     *   Punto 1: B2C + esito NS(31)                              → backup
     *   Punto 2: B2C + canaleSecondario SI + esito RC(30)/MC(32)  → spostamento
     *   Punto 3-4: B2B + canaleSecondario NO + esito RC(30)/MC(32) → spostamento
     *   Punto 5: PA/INCO + esito NS(31)/CK(39)                   → backup
     *   Punto 6: PA/INCO + esito RC(30)/MC(32)                    → spostamento
     */
    private fun eseguiOperazioneFile(archivio: ArchivioRequest): Boolean {
        return try {
            val categoria = archivio.categoriaStampa
            val esito = archivio.esitoSdi
            val canaleSecTipo = archivio.tipoCanaleSecondario

            when {
                categoria == "B2C" && esito == 31 -> {
                    logger.info("Caso IDRICO Punto 1: B2C + NS → backup")
                    spostaFileDmsInBackup(archivio)
                }

                categoria == "B2C" && canaleSecTipo == "SI" && esito in listOf(30, 32) -> {
                    logger.info("Caso IDRICO Punto 2: B2C + SI + RC/MC → spostamento")
                    spostaFileDms(archivio)
                }

                categoria == "B2B" && canaleSecTipo == "NO" && esito in listOf(30, 32) -> {
                    logger.info("Caso IDRICO Punto 3-4: B2B + NO + RC/MC → spostamento")
                    spostaFileDms(archivio)
                }

                categoria in CATEGORIE_PA_INCO && esito in listOf(31, 39) -> {
                    logger.info("Caso IDRICO Punto 5: PA/INCO + NS/CK → backup")
                    spostaFileDmsInBackup(archivio)
                }

                categoria in CATEGORIE_PA_INCO && esito in listOf(30, 32) -> {
                    logger.info("Caso IDRICO Punto 6: PA/INCO + RC/MC → spostamento")
                    spostaFileDms(archivio)
                }

                else -> {
                    logger.warn(
                        "Nessuna azione prevista per categoria={}, canaleSecondario={}, tipoCanaleSecondario={}, esito={}",
                        categoria, archivio.canaleSecondario, canaleSecTipo, esito
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

    /**
     * Determina la directory di destinazione per i file DMS.
     *
     * Priorità:
     *   1. dmsDestPath esplicito dalla request
     *   2. MASSIVI_OUTBOUND_SAPDOCID se sapDocId è presente
     *   3. MASSIVI_OUTBOUND_NO_SAPDOCID altrimenti
     */
    private fun risolviDestinazioneDms(archivio: ArchivioRequest): String {
        archivio.dmsDestPath?.let {
            return fileStorageService.normalizePath(it)
        }

        return if (!archivio.sapDocId.isNullOrBlank()) {
            sapDocIdDestPath(archivio.societa)
        } else {
            noSapDocIdDestPath(archivio.societa)
        }
    }

    /**
     * Estrae directory e nome base dal campo nomeFileDms, normalizzando il path.
     *
     * Gestisce input come:
     *   "C:/Users/.../df-share/AAT5_OUTBOUND/COPIA_RDI/2026/03/31/14/IT01234"
     *     → Pair("AAT5_OUTBOUND/COPIA_RDI/2026/03/31/14", "IT01234")
     *   "\\zenit.aceaspa.it\Idrico_collaudo_DMS_DATI$\AAT2_OUTBOUND\SUB\IT01234"
     *     → Pair("AAT2_OUTBOUND/SUB", "IT01234")
     *   "IT01234"
     *     → Pair(null, "IT01234")
     */
    private fun splitSourcePath(nomeFileDms: String): Pair<String?, String> {
        val normalized = fileStorageService.normalizePath(nomeFileDms)
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) {
            Pair(normalized.substring(0, lastSlash), normalized.substring(lastSlash + 1))
        } else {
            Pair(null, normalized)
        }
    }

    /**
     * Costruisce il nome file con estensione, evitando duplicazione.
     * "IT01234" + "pdf" → "IT01234.pdf"
     * "IT01234.pdf" + "pdf" → "IT01234.pdf" (non "IT01234.pdf.pdf")
     */
    private fun fileNameWithExtension(baseName: String, extension: String): String {
        return if (baseName.endsWith(".$extension", ignoreCase = true)) baseName
        else "$baseName.$extension"
    }

    private fun spostaFileDms(archivio: ArchivioRequest): Boolean {
        val (sourceDir, fileBaseName) = splitSourcePath(archivio.nomeFileDms)
        val destDir = risolviDestinazioneDms(archivio)

        logger.info(
            "Spostamento file DMS: base={}, sourceDir={}, destDir={}",
            fileBaseName, sourceDir, destDir
        )

        val pdfOk = moveFileByExtension(sourceDir, fileBaseName, "pdf", destDir, overwrite = false)
        val xmlOk = moveFileByExtension(sourceDir, fileBaseName, "xml", destDir, overwrite = false)

        if (!pdfOk || !xmlOk) {
            val pdfAtDest = fileStorageService.fileExists(destDir, fileNameWithExtension(fileBaseName, "pdf"))
            val xmlAtDest = fileStorageService.fileExists(destDir, fileNameWithExtension(fileBaseName, "xml"))

            if (pdfAtDest && xmlAtDest) {
                logger.info(
                    "File già presenti alla destinazione (probabile re-delivery Kafka): base={}, destDir={}",
                    fileBaseName, destDir
                )
                return true
            }
        }

        logger.info(
            "Spostamento completato per base={}: pdf={}, xml={}",
            fileBaseName, pdfOk, xmlOk
        )
        return pdfOk && xmlOk
    }

    private fun spostaFileDmsInBackup(archivio: ArchivioRequest): Boolean {
        val (sourceDir, fileBaseName) = splitSourcePath(archivio.nomeFileDms)
        val destDir = bckDataDestPath(archivio.societa)

        logger.info(
            "Spostamento file DMS in backup: base={}, sourceDir={}, destDir={}",
            fileBaseName, sourceDir, destDir
        )

        val pdfOk = moveFileByExtension(sourceDir, fileBaseName, "pdf", destDir, overwrite = true)
        val xmlOk = moveFileByExtension(sourceDir, fileBaseName, "xml", destDir, overwrite = true)

        if (!pdfOk || !xmlOk) {
            val pdfAtDest = fileStorageService.fileExists(destDir, fileNameWithExtension(fileBaseName, "pdf"))
            val xmlAtDest = fileStorageService.fileExists(destDir, fileNameWithExtension(fileBaseName, "xml"))

            if (pdfAtDest && xmlAtDest) {
                logger.info(
                    "File già presenti in backup (probabile re-delivery Kafka): base={}, destDir={}",
                    fileBaseName, destDir
                )
                return true
            }
        }

        logger.info(
            "Spostamento in backup completato per base={}: pdf={}, xml={}",
            fileBaseName, pdfOk, xmlOk
        )
        return pdfOk && xmlOk
    }

    /**
     * Sposta un singolo file (baseName.extension) da sourceDir a destDir.
     * Se baseName ha già l'estensione (es. "file.pdf"), non la duplica.
     */
    private fun moveFileByExtension(
        sourceDir: String?,
        baseName: String,
        extension: String,
        destDir: String,
        overwrite: Boolean
    ): Boolean {
        val fileName = fileNameWithExtension(baseName, extension)
        val sourcePath = fileStorageService.combinePath(sourceDir, fileName)
        val destPath = fileStorageService.combinePath(destDir, fileName)

        logger.info("Move: {} -> {} (overwrite={})", sourcePath, destPath, overwrite)

        return fileStorageService.moveFile(sourcePath, destPath, overwrite)
    }
}
