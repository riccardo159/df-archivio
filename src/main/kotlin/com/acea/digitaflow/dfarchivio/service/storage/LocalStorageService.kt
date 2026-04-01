package com.acea.digitaflow.dfarchivio.service.storage

import com.acea.digitalflow.df_common.dto.StorageHealthStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Implementazione Local Storage per mount locale (NFS/CIFS)
 *
 * Attiva quando: storage.type = local
 */
@Service
@ConditionalOnProperty(
    name = ["storage.type"],
    havingValue = "local"
)
class LocalStorageService(
    @Value("\${storage.local.mount-path}")
    private val mountPath: String,
    @Value("\${ZENIT_SMB_AMBIENTE:collaudo}")
    private val ambiente: String,

    ) : FileStorageService {
    val shareResolved = "Idrico_${ambiente}_DMS_DATI$"

    private val logger = LoggerFactory.getLogger(LocalStorageService::class.java)
    private val baseDir: Path = Paths.get(mountPath).toAbsolutePath()

    init {
        logger.info("LocalStorageService initialized with mount path: {}", baseDir)

        if (!baseDir.toFile().exists()) {
            logger.warn("Mount path non trovato: {}. Ops deve montare la share.", baseDir)
        } else {
            logger.info("Mount path disponibile: {}", baseDir)
        }
    }

    override fun listFiles(relativePath: String?): List<String> {
        val dir = resolveAndValidatePath(relativePath)
        logger.info("Local list files: {}", dir)

        val dirFile = dir.toFile()
        if (!dirFile.exists() || !dirFile.isDirectory) {
            logger.warn("Directory non trovato: {}", dir)
            return emptyList()
        }

        return dirFile.listFiles()
            ?.filter { it.name != "." && it.name != ".." }
            ?.map { it.name }
            ?: emptyList()
    }

    override fun downloadFile(relativeDir: String?, fileName: String): ByteArray {
        val dir = resolveAndValidatePath(relativeDir)
        val file = dir.resolve(fileName).toFile()
        logger.info("Local download: {}", file.absolutePath)

        if (!file.exists()) {
            throw FileNotFoundException("File non trovato: $fileName")
        }

        return file.readBytes()
    }

    override fun uploadFile(relativeDir: String?, fileName: String, content: ByteArray) {
        val dir = resolveAndValidatePath(relativeDir)
        val file = dir.resolve(fileName)
        logger.info("Local upload: {} ({} bytes)", file, content.size)

        Files.createDirectories(file.parent)
        file.toFile().writeBytes(content)
    }

    override fun fileExists(relativeDir: String?, fileName: String): Boolean {
        val dir = resolveAndValidatePath(relativeDir)
        val file = dir.resolve(fileName).toFile()
        return file.exists()
    }

    override fun ensureDirectory(relativeDir: String?) {
        if (relativeDir.isNullOrBlank()) return
        val dir = resolveAndValidatePath(relativeDir)
        logger.info("Local mkdir: {}", dir)
        Files.createDirectories(dir)
    }

    override fun deleteFile(relativeDir: String?, fileName: String) {
        val dir = resolveAndValidatePath(relativeDir)
        val file = dir.resolve(fileName).toFile()
        logger.info("Local delete: {}", file.absolutePath)

        if (!file.delete()) {
            throw RuntimeException("Impossibile eliminare file: $fileName")
        }
    }

    override fun deleteFileIfExists(relativeDir: String?, fileName: String) {
        val dir = resolveAndValidatePath(relativeDir)
        val file = dir.resolve(fileName).toFile()

        if (file.exists()) {
            logger.info("Local delete (exists): {}", file.absolutePath)
            file.delete()
        } else {
            logger.debug("Local delete skipped (not found): {}", file.absolutePath)
        }
    }


    private fun resolveAndValidatePath(relativePath: String?): Path {
        var normalized = relativePath?.trim() ?: ""

        // SE È UN UNC PATH (inizia con \\)
        if (normalized.startsWith("\\\\")) {
            var path = normalized.removePrefix("\\\\")
            val firstBackslashIndex = path.indexOf("\\")

            if (firstBackslashIndex != -1) {
                path = path.substring(firstBackslashIndex + 1)
                normalized = path
            }
        }

        // Normalizza separatori
        normalized = normalized.replace("\\", "/").trimStart('/')

        // Controlla il pattern "Idrico_*_DMS_DATI$" (non importa l'ambiente)
        val sharePattern = Regex("""Idrico_\w+_DMS_DATI\$""")
        if (!sharePattern.containsMatchIn(normalized)) {
            normalized = "$shareResolved/$normalized"
            logger.debug("Aggiunta share al path: {}", normalized)
        } else {
            logger.debug("Share con pattern Idrico_*_DMS_DATI$ già presente nel path")
        }

        // Resolve e valida
        val resolved = baseDir.resolve(normalized).normalize()

        if (!resolved.startsWith(baseDir)) {
            throw IllegalArgumentException("Path traversal non permesso: $relativePath")
        }

        return resolved
    }

    override fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean): Boolean {
        return try {
            val src = resolveAndValidatePath(sourcePath)
            val dst = resolveAndValidatePath(destPath)

            if (!Files.exists(src)) {
                logger.warn("dvato (local): {}", src)
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


    override fun getHealthStatus(): StorageHealthStatus {
        return try {
            val file = baseDir.toFile()
            val isConnected = file.exists() && file.isDirectory

            StorageHealthStatus(
                isConnected = isConnected,
                storageType = "LOCAL",
                basePath = baseDir.toString(),
                lastConnectionTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            StorageHealthStatus(
                isConnected = false,
                storageType = "LOCAL",
                basePath = baseDir.toString(),
                errorMessage = e.message,
                lastConnectionTime = System.currentTimeMillis()
            )
        }
    }
}