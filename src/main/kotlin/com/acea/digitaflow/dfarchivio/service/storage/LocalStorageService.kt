package com.acea.digitaflow.dfarchivio.service.storage

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
 *
 * Il mount-path punta alla root della share (es: /mnt/dh-share).
 * Tutti i path relativi partono da lì (es: AAT2_OUTBOUND/...).
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
            logger.warn("Directory non trovata: {}", dir)
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
            throw FileNotFoundException("File non trovato: $fileName in $relativeDir")
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

    override fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean): Boolean {
        return try {
            val src = resolveAndValidatePath(sourcePath)
            val dst = resolveAndValidatePath(destPath)

            if (!Files.exists(src)) {
                logger.warn("File sorgente non trovato: {}", src)
                return false
            }

            // Crea le directory di destinazione se non esistono
            Files.createDirectories(dst.parent)

            // Usa Files.move atomico con le opzioni appropriate
            val options = buildList {
                add(StandardCopyOption.ATOMIC_MOVE)
                if (overwrite) add(StandardCopyOption.REPLACE_EXISTING)
            }.toTypedArray()

            try {
                Files.move(src, dst, *options)
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                // Fallback: se ATOMIC_MOVE non è supportato (cross-device), copy + delete
                logger.debug("ATOMIC_MOVE non supportato, fallback copy+delete: {} -> {}", src, dst)
                val copyOptions = if (overwrite) {
                    arrayOf(StandardCopyOption.REPLACE_EXISTING)
                } else {
                    emptyArray()
                }
                Files.copy(src, dst, *copyOptions)
                Files.deleteIfExists(src)
            }

            logger.info("File spostato con successo: {} -> {}", src, dst)
            true
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            logger.error("File di destinazione già esistente (overwrite=false): {}", destPath)
            false
        } catch (e: Exception) {
            logger.error("Errore spostamento file {} -> {}: {}", sourcePath, destPath, e.message, e)
            false
        }
    }

    /**
     * Risolve un path relativo contro la baseDir, normalizzando eventuali
     * prefissi UNC legacy tramite il metodo dell'interfaccia.
     *
     * Garantisce che il path risolto resti dentro la baseDir (path traversal check).
     */
    private fun resolveAndValidatePath(relativePath: String?): Path {
        // Delega la normalizzazione all'interfaccia (rimuove UNC, uniforma separatori)
        val normalized = normalizePath(relativePath)

        val resolved = baseDir.resolve(normalized).normalize()

        if (!resolved.startsWith(baseDir)) {
            throw IllegalArgumentException("Path traversal non permesso: $relativePath -> $resolved")
        }

        return resolved
    }
}
