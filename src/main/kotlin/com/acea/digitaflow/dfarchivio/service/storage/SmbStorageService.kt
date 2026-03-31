package com.acea.digitaflow.dfarchivio.service.storage

import com.acea.digitalflow.df_common.dto.StorageHealthStatus
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.util.*

/**
 * Implementazione SMB Storage usando smbj
 *
 * Attiva quando: storage.type = smb
 */
@Service
@ConditionalOnProperty(
    name = ["storage.type"],
    havingValue = "smb",
    matchIfMissing = true  // Default: SMB
)
class SmbStorageService(
    private val configProvider: SmbConfigService
) : FileStorageService {

    private val logger = LoggerFactory.getLogger(SmbStorageService::class.java)
    private var lastHealthCheck = System.currentTimeMillis()

    /**
     * Template method per connessione SMB
     */
    private fun <T> withShare(block: (DiskShare) -> T): T {
        val props = configProvider.getEffectiveConfig("DEFAULT")

        val config = com.hierynomus.smbj.SmbConfig.builder()
            .withTimeout(props.connectionTimeout.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .withSoTimeout(props.soTimeout.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        SMBClient(config).use { smbClient ->
            val server = props.server
            val port = props.port

            logger.debug(
                "SMB session -> {}:{} share='{}' basePath='{}' timeout={}ms",
                server, port, props.share, props.basePath, props.connectionTimeout
            )

            val connection = smbClient.connect(server, port)
            try {
                val ac = AuthenticationContext(
                    props.username,
                    props.password.toCharArray(),
                    props.domain.takeIf { it?.isNotBlank() == true }
                )

                val session = connection.authenticate(ac)
                val share = session.connectShare(props.share) as DiskShare

                share.use {
                    lastHealthCheck = System.currentTimeMillis()
                    return block(it)
                }
            } catch (e: Exception) {
                logger.error("Errore SMB session verso $server:$port", e)
                throw RuntimeException("Errore connessione SMB: ${e.message}", e)
            } finally {
                runCatching { connection.close() }
            }
        }
    }

    override fun listFiles(relativePath: String?): List<String> {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val dir = buildPath(props.basePath, relativePath)
        logger.info("SMB list files: {}", dir)

        return withShare { share ->
            try {
                share.list(dir, "*")
                    .filter { it.fileName != "." && it.fileName != ".." }
                    .map(FileIdBothDirectoryInformation::getFileName)
            } catch (e: Exception) {
                if (isPathNotFound(e)) {
                    logger.warn("SMB dir non trovato: {}", dir)
                    emptyList()
                } else {
                    throw e
                }
            }
        }
    }

    override fun downloadFile(relativeDir: String?, fileName: String): ByteArray {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val dir = buildPath(props.basePath, relativeDir)
        val fullPath = "$dir/$fileName"
        logger.info("SMB download: {}", fullPath)

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

    override fun uploadFile(relativeDir: String?, fileName: String, content: ByteArray) {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val dir = buildPath(props.basePath, relativeDir)
        val fullPath = "$dir/$fileName"
        logger.info("SMB upload: {} ({} bytes)", fullPath, content.size)

        withShare { share ->
            createDirectoriesRecursively(share, dir)
            val file = share.openFile(
                fullPath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            file.use { f ->
                ByteArrayInputStream(content).use { input ->
                    f.outputStream.use { out ->
                        input.copyTo(out)
                    }
                }
            }
        }
    }

    override fun fileExists(relativeDir: String?, fileName: String): Boolean {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val dir = buildPath(props.basePath, relativeDir)
        val fullPath = "$dir/$fileName"

        return withShare { share ->
            try {
                share.fileExists(fullPath)
            } catch (e: Exception) {
                logger.warn("Errore controllo esistenza: {}", fullPath)
                false
            }
        }
    }

    override fun ensureDirectory(relativeDir: String?) {
        if (relativeDir.isNullOrBlank()) return
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val dir = buildPath(props.basePath, relativeDir)

        withShare { share ->
            createDirectoriesRecursively(share, dir)
        }
    }

    override fun deleteFile(relativeDir: String?, fileName: String) {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val dir = buildPath(props.basePath, relativeDir)
        val fullPath = "$dir/$fileName"
        logger.info("SMB delete: {}", fullPath)

        withShare { share ->
            share.rm(fullPath)
        }
    }

    override fun deleteFileIfExists(relativeDir: String?, fileName: String) {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val dir = buildPath(props.basePath, relativeDir)
        val fullPath = "$dir/$fileName"

        withShare { share ->
            try {
                if (share.fileExists(fullPath)) {
                    logger.info("SMB delete (exists): {}", fullPath)
                    share.rm(fullPath)
                } else {
                    logger.debug("SMB delete skipped (not found): {}", fullPath)
                }
            } catch (e: Exception) {
                logger.warn("Errore delete: {}", fullPath, e)
            }
        }
    }

    private fun buildPath(basePath: String, relativeDir: String?): String {
        val base = basePath.trim().trim('/', '\\')
        val rel = (relativeDir ?: "").trim().trim('/', '\\')
        val combined = when {
            base.isBlank() -> rel
            rel.isBlank() -> base
            else -> "$base/$rel"
        }
        return combined
            .trim('/', '\\')
            .replace('/', '\\')
    }

    private fun createDirectoriesRecursively(share: DiskShare, path: String) {
        val normalized = path.replace('\\', '/')
        val parts = normalized.split("/").filter { it.isNotBlank() }
        if (parts.isEmpty()) return

        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            if (!share.folderExists(current)) {
                logger.debug("SMB mkdir: {}", current)
                share.mkdir(current)
            }
        }
    }

    private fun isPathNotFound(e: Exception): Boolean =
        e is com.hierynomus.mssmb2.SMBApiException &&
                (e.message?.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true) == true ||
                        e.message?.contains("0xc000003a", ignoreCase = true) == true)


    override fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean): Boolean {
        return withShare { share ->
            try {
                val sourceFile = share.openFile(
                    sourcePath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    EnumSet.noneOf(FileAttributes::class.java),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    EnumSet.noneOf(SMB2CreateOptions::class.java)
                )

                // Crea directory di destinazione se non esiste
                val destDir = destPath.substringBeforeLast('/')
                createDirectoriesRecursively(share, destDir)

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

                if (overwrite) {
                    share.rm(sourcePath)
                }

                logger.info("File spostato con successo (smb): {} -> {}", sourcePath, destPath)
                true
            } catch (e: Exception) {
                logger.error("Errore spostamento file (smb) {} -> {}: {}", sourcePath, destPath, e.message, e)
                false
            }
        }
    }

    override fun getHealthStatus(): StorageHealthStatus {
        return try {
            // Step 1: Leggi configurazione (può fallire)
            val props = try {
                configProvider.getEffectiveConfig("DEFAULT")
            } catch (configEx: Exception) {
                logger.error("Config non trovata", configEx)
                return StorageHealthStatus(
                    isConnected = false,
                    storageType = "SMB",
                    basePath = "N/A",
                    errorMessage = "Config error: ${configEx.message}",
                    lastConnectionTime = System.currentTimeMillis()
                )
            }

            // Step 2: Testa connessione (può fallire)
            withShare { share ->
                share.list("/")

                StorageHealthStatus(
                    isConnected = true,
                    storageType = "SMB",
                    basePath = props.basePath,
                    lastConnectionTime = System.currentTimeMillis()
                )
            }
        } catch (connEx: Exception) {
            logger.error("Errore connessione SMB", connEx)
            StorageHealthStatus(
                isConnected = false,
                storageType = "SMB",
                basePath = "",
                errorMessage = "Connection error: ${connEx.message}",
                lastConnectionTime = System.currentTimeMillis()
            )
        }
    }
}
