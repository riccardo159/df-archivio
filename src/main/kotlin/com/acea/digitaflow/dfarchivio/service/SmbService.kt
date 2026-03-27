package com.acea.digitaflow.dfarchivio.service

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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.EnumSet

@Service
class SmbService(
    private val configProvider: SmbConfigService,
    @Value("\${storage.mode:smb}")
    private val storageMode: String,
    @Value("\${storage.local.path:/df-share}")
    private val localBasePath: String
) {

    private val logger = LoggerFactory.getLogger(SmbService::class.java)

    private val isLocal get() = storageMode.equals("local", ignoreCase = true)

    fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean): Boolean {
        logger.info("Spostamento file [mode={}]: {} -> {} (overwrite={})", storageMode, sourcePath, destPath, overwrite)
        return if (isLocal) moveFileLocal(sourcePath, destPath, overwrite)
        else moveFileSmb(sourcePath, destPath, overwrite)
    }

    fun fileExists(relativePath: String): Boolean {
        return if (isLocal) fileExistsLocal(relativePath)
        else withShare { share -> fileExistsSmb(share, relativePath) }
    }


    fun listFiles(relativeDir: String? = null): List<String> {
        return if (isLocal) listFilesLocal(relativeDir)
        else withShare { share -> listFilesSmb(share, relativeDir) }
    }

    fun buildPath(relativeDir: String?): String {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        return if (relativeDir.isNullOrBlank()) props.basePath.trimEnd('/')
        else props.basePath.trimEnd('/') + "/" + relativeDir.trim('/')
    }

    private fun resolveLocalPath(relativePath: String?): Path {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        val base = Path.of(localBasePath, props.basePath)
        return if (relativePath.isNullOrBlank()) base
        else base.resolve(relativePath.trim('/'))
    }

    private fun moveFileLocal(sourcePath: String, destPath: String, overwrite: Boolean): Boolean {
        return try {
            val src = resolveLocalPath(sourcePath)
            val dst = resolveLocalPath(destPath)

            if (!Files.exists(src)) {
                logger.warn("File sorgente non trovato (local): {}", src)
                return false
            }

            Files.createDirectories(dst.parent)

            if (overwrite) {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                Files.deleteIfExists(src)
            } else {
                Files.copy(src, dst)
            }

            logger.info("File spostato con successo (local): {} -> {}", src, dst)
            true
        } catch (e: Exception) {
            logger.error("Errore spostamento file (local) {} -> {}: {}", sourcePath, destPath, e.message, e)
            false
        }
    }

    private fun fileExistsLocal(relativePath: String): Boolean {
        val path = resolveLocalPath(relativePath)
        val exists = Files.exists(path)
        logger.debug("Check file exists (local): {} -> {}", path, exists)
        return exists
    }

    private fun listFilesLocal(relativeDir: String?): List<String> {
        val dir = resolveLocalPath(relativeDir)
        logger.info("Listing dir (local): {}", dir)

        return try {
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                logger.warn("Directory non trovata (local): {} (skip)", dir)
                return emptyList()
            }
            Files.list(dir).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            }
        } catch (e: Exception) {
            logger.error("Errore listing directory (local) {}: {}", dir, e.message, e)
            emptyList()
        }
    }

    private fun moveFileSmb(sourcePath: String, destPath: String, overwrite: Boolean): Boolean {
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

    private fun listFilesSmb(share: DiskShare, relativeDir: String? = null): List<String> {
        val dir = buildPath(relativeDir)
        logger.info("Listing SMB dir: {}", dir)

        return try {
            share.list(dir, "*")
                .filter { it.fileName != "." && it.fileName != ".." }
                .map(FileIdBothDirectoryInformation::getFileName)
        } catch (e: Exception) {
            if (isPathNotFound(e)) {
                logger.warn("SMB dir non trovato: {} (skip)", dir)
                emptyList()
            } else {
                throw e
            }
        }
    }

    private fun fileExistsSmb(share: DiskShare, fullPath: String): Boolean {
        val normalized = normalizeSmbPath(fullPath)
        logger.debug("Check SMB exists: {}", normalized)
        return share.fileExists(normalized)
    }

    /**
     * Restituisce una sessione SMB e la DiskShare per la share configurata.
     */
    fun <T> withShare(block: (DiskShare) -> T): T {
        val props = configProvider.getEffectiveConfig("DEFAULT")

        val config = com.hierynomus.smbj.SmbConfig.builder()
            .withTimeout(props.connectionTimeout.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .withSoTimeout(props.soTimeout.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        SMBClient(config).use { smbClient ->
            val server = props.server
            val port = props.port

            logger.info(
                "SMB session -> {}:{} share='{}' basePath='{}' timeout={}ms soTimeout={}ms",
                server, port, props.share, props.basePath, props.connectionTimeout, props.soTimeout
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

                share.use { return block(it) }
            } catch (e: Exception) {
                throw smbException("Errore SMB session verso $server:$port", e)
            } finally {
                runCatching { connection.close() }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Utility private
    // ══════════════════════════════════════════════════════════

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

    private fun isPathNotFound(e: Exception): Boolean =
        e is com.hierynomus.mssmb2.SMBApiException &&
                (e.message?.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true) == true ||
                        e.message?.contains("0xc000003a", ignoreCase = true) == true)

    private fun normalizeSmbPath(path: String): String =
        path.trim().trimStart('/', '\\').replace('/', '\\')

    private fun smbException(msg: String, e: Throwable): RuntimeException {
        val root = rootCause(e)
        logger.error("ERRORE SMB: {}. rootCause={} - {}", msg, root.javaClass.name, root.message, e)
        return RuntimeException("$msg: ${root.message}", e)
    }

    private fun rootCause(t: Throwable): Throwable {
        var cur: Throwable = t
        while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!
        return cur
    }
}
