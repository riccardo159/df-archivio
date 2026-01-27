package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.config.SmbProperties
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.util.EnumSet

@Service
class SmbService(
    private val props: SmbProperties
) {

    private val logger = LoggerFactory.getLogger(SmbService::class.java)

    /**
     * Restituisce una sessione SMB e la DiskShare per la share configurata.
     * Usa un `use` annidato in ogni metodo pubblico.
     */
    fun <T> withShare(block: (DiskShare) -> T): T {
        val client = SMBClient()
        client.use { smbClient ->
            logger.debug("Connessione SMB a server ${props.server}, share ${props.share}")

            val connection = smbClient.connect(props.server)

            val ac = AuthenticationContext(
                props.username,
                props.password.toCharArray(),
                props.domain
            )

            val session = connection.authenticate(ac)
            val share = session.connectShare(props.share) as DiskShare

            share.use {
                return block(it)
            }
        }
    }

    /**
     * Lista i file nella directory base (COMPUNET) o in una sotto directory relativa.
     */
    fun listFiles(relativeDir: String? = null): List<String> {
        val dir = buildPath(relativeDir)
        logger.info("Listing file SMB su $dir")

        return withShare { share ->
            val entries = share.list(dir, "*")  // puoi filtrare es. "*.PDF"[web:244][web:278]
            entries
                .filter { !it.fileName.equals(".", true) && !it.fileName.equals("..", true) }
                .map(FileIdBothDirectoryInformation::getFileName)
        }
    }

    /**
     * Scarica un file e restituisce il contenuto come ByteArray.
     */
    fun downloadFile(relativeDir: String? = null, fileName: String): ByteArray {
        val dir = buildPath(relativeDir)
        val fullPath = "$dir/$fileName"
        logger.info("Download file da SMB: $fullPath")

        return withShare { share ->
            val file = share.openFile(
                fullPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.noneOf(FileAttributes::class.java),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            )

            file.use { f ->
                f.inputStream.use { input ->
                    input.readAllBytes()
                }
            }
        }
    }

    /**
     * Upload di un file (sovrascrive se esiste).
     */
    fun uploadFile(relativeDir: String? = null, fileName: String, content: ByteArray) {
        val dir = buildPath(relativeDir)
        val fullPath = "$dir/$fileName"
        logger.info("Upload file su SMB: $fullPath, size=${content.size} bytes")

        withShare { share ->
            // Crea le directory intermedie se non esistono
            createDirectoriesRecursively(share, dir)

            val file = share.openFile(
                fullPath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,   // crea o sovrascrive[web:278][web:266]
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )

            file.use { f ->
                ByteArrayInputStream(content).use { input ->
                    val out = f.outputStream
                    out.use { o ->
                        input.copyTo(o)
                    }
                }
            }
        }
    }

    /**
     * Costruisce il path completo relativo alla share, partendo da basePath.
     */
    fun buildPath(relativeDir: String?): String =
        if (relativeDir.isNullOrBlank()) props.basePath.trimEnd('/')
        else props.basePath.trimEnd('/') + "/" + relativeDir.trim('/')

    /**
     * Crea ricorsivamente la gerarchia di directory (se non esistono) all'interno della share.
     */
    private fun createDirectoriesRecursively(share: DiskShare, path: String) {
        val parts = path.split("/").filter { it.isNotBlank() }
        if (parts.isEmpty()) return

        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            if (!share.folderExists(current)) {
                logger.debug("Creo directory SMB: $current")
                share.mkdir(current)
            }
        }
    }

    fun fileExists(fullPath: String): Boolean {
        logger.debug("Verifica esistenza file SMB: $fullPath")
        return withShare { share ->
            share.fileExists(fullPath)
        }
    }

}
