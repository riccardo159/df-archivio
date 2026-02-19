package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitaflow.dfarchivio.request.ArchivioRequest
import com.acea.digitalflow.df_common.dto.ArchivioDto
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.enums.CanaleSecondario
import com.acea.digitalflow.df_common.enums.TipoDocumento
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import java.util.EnumSet

@Service
class ArchivioService(
    private val sbmService: SmbService,
    private val notificationProducerService: NotificationProducerService
) {
    private fun sapDocIdDestPath(societa: String): String =
        sbmService.buildPath(societa + "_OUTBOUND/MASSIVI_OUTBOUND_SAPDOCID")

    private fun noSapDocIdDestPath(societa: String): String =
        sbmService.buildPath(societa + "_OUTBOUND/MASSIVI_OUTBOUND_NO_SAPDOCID")
    private val logger = LoggerFactory.getLogger(ArchivioService::class.java)

    fun forward(archivi: List<ArchivioDto>) {
        logger.info("Forward Archivio verso notification - count={}", archivi.size)

        val requests = archivi.map { archivio ->
            ArchivioNotificationRequest(
                tipoCanaleSecondario = CanaleSecondario.ARCHIVIO,
                flgCanaleSecondario = true,
                tipoDocumento = TipoDocumento.ARCHIVIAZIONE,
                datiDocumento = archivio,
                societa = archivio.societa,
                dcsId = archivio.dcsId
            )
        }

        notificationProducerService.inviaNotifica(requests)
    }


    fun archiviazione(archivio: ArchivioRequest) {
        logger.info("Avvio archiviazione DMS per dcsId=${archivio.dcsId}, categoria=${archivio.categoriaStampa}, esito=${archivio.esitoSdi}, canaleSecondario=${archivio.canaleSecondario}")

        when {
            // Punto 1: B2C / B2C, esito 31(NS) -> Cancella file DMS
            archivio.categoriaStampa == "B2C" &&
                    archivio.canaleSecondario == "B2C" &&
                    archivio.esitoSdi == 31 -> {
                logger.info("Caso IDRICO Punto 1: cancellazione file DMS")
                cancellaFileDms(archivio)
            }

            // Punto 2: B2C / B2C, canaleSecondario = SI, esito 30(RC) o 32(MC) -> Spostamento file
            archivio.categoriaStampa == "B2C" &&
                    archivio.canaleSecondario == "B2C" &&
                    archivio.tipoCanaleSecondario == "SI" &&
                    (archivio.esitoSdi == 30 || archivio.esitoSdi == 32) -> {
                logger.info("Caso IDRICO Punto 2: spostamento file DMS")
                spostaFileDms(archivio)
            }

            // Punto 3: B2B / B2C, canaleSecondario = NO, esito 32(MC) -> Spostamento file
            archivio.categoriaStampa == "B2B" &&
                    archivio.canaleSecondario == "B2C" &&
                    archivio.tipoCanaleSecondario == "NO" &&
                    archivio.esitoSdi == 32 -> {
                logger.info("Caso IDRICO Punto 3: spostamento file DMS")
                spostaFileDms(archivio)
            }

            // Punto 4: B2B / B2C, canaleSecondario = NO, esito 30(RC) -> Spostamento file
            archivio.categoriaStampa == "B2B" &&
                    archivio.canaleSecondario == "B2C" &&
                    archivio.tipoCanaleSecondario == "NO" &&
                    archivio.esitoSdi == 30 -> {
                logger.info("Caso IDRICO Punto 4: spostamento file DMS")
                spostaFileDms(archivio)
            }

            // Punto 5: PA / INCO, esito 31(NS) o 39(CK) -> Cancella file DMS
            archivio.categoriaStampa in listOf("PA", "INCO") &&
                    (archivio.esitoSdi == 31 || archivio.esitoSdi == 39) -> {
                logger.info("Caso IDRICO Punto 5: cancellazione file DMS")
                cancellaFileDms(archivio)
            }

            // Punto 6: PA / INCO, esito 30(RC) o 32(MC) -> Spostamento file
            archivio.categoriaStampa in listOf("PA", "INCO") &&
                    (archivio.esitoSdi == 30 || archivio.esitoSdi == 32) -> {
                logger.info("Caso IDRICO Punto 6: spostamento file DMS")
                spostaFileDms(archivio)
            }

            else -> {
                logger.warn("Nessuna azione prevista per combinazione categoria=${archivio.categoriaStampa}, canaleSecondario=${archivio.canaleSecondario}, tipoCanaleSecondario=${archivio.tipoCanaleSecondario}, esito=${archivio.esitoSdi}")
            }
        }
    }

    private fun risolviDestinazioneDms(archivio: ArchivioRequest): String {
        archivio.dmsDestPath?.let { return sbmService.buildPath(it) }

        return if (!archivio.sapDocId.isNullOrBlank()) {
            sapDocIdDestPath(archivio.societa)
        } else {
            noSapDocIdDestPath(archivio.societa)
        }
    }

    private fun spostaFileDms(archivio: ArchivioRequest) {
        // nomeFileDms: path + base name senza estensione (da doc)
        // Qui ipotizzo che nomeFileDms includa la directory sorgente relativa (es: "subdir/filebase")
        // e che tu debba spostare PDF e XML.
        val sourceBase = archivio.nomeFileDms // es: "path/filename_senza_ext"

        val destDir = risolviDestinazioneDms(archivio)

        // Sostituire con la logica esatta per separare dir/base name
        val lastSlash = sourceBase.lastIndexOf('/')
        val sourceDir = if (lastSlash >= 0) sourceBase.substring(0, lastSlash) else null
        val fileBaseName = if (lastSlash >= 0) sourceBase.substring(lastSlash + 1) else sourceBase

        // PDF
        val pdfName = "$fileBaseName.pdf"
        val pdfOk = moveFile(sourceDir, pdfName, destDir, sovrascrivi = false)

        // XML
        val xmlName = "$fileBaseName.xml"
        val xmlOk = moveFile(sourceDir, xmlName, destDir, sovrascrivi = false)

        logger.info("Spostamento file DMS completato per base=$fileBaseName, pdfOk=$pdfOk, xmlOk=$xmlOk")
    }

    private fun cancellaFileDms(archivio: ArchivioRequest) {
        // nel documento: "si prende il valore di sNomeFileDMS e si cancellano i relativi file (PDF e XML)"[file:33]
        val sourceBase = archivio.nomeFileDms

        val lastSlash = sourceBase.lastIndexOf('/')
        val sourceDir = if (lastSlash >= 0) sourceBase.substring(0, lastSlash) else null
        val fileBaseName = if (lastSlash >= 0) sourceBase.substring(lastSlash + 1) else sourceBase

        val pdfName = "$fileBaseName.pdf"
        val xmlName = "$fileBaseName.xml"

        // Per cancellare puoi usare moveFile verso una "trash" share con sovrascrivi=true,
        // oppure aggiungere un metodo deleteFile in SmbService.
        logger.info("TODO: implementare cancellazione SMB per $sourceDir/$pdfName e $sourceDir/$xmlName")
    }

    fun moveFile(sourceRelativeDir: String?, sourceFileName: String, destRelativeDir: String?, sovrascrivi: Boolean): Boolean {
        val sourceDir = sbmService.buildPath(sourceRelativeDir)
        val destDir = sbmService.buildPath(destRelativeDir)
        val sourcePath = "$sourceDir/$sourceFileName"
        val destPath = "$destDir/$sourceFileName"

        logger.info("Spostamento file SMB: $sourcePath -> $destPath")

        return sbmService.withShare { share ->
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

                logger.info("File spostato con successo: $sourcePath -> $destPath")
                true
            } catch (e: Exception) {
                logger.error("Errore spostamento file $sourcePath -> $destPath", e)
                false
            }
        }
    }
}