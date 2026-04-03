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
 * Il mount-path punta alla root della share (es: /mnt/dh-share o C:\Users\...\df-share).
 * Tutti i path relativi partono da lì (es: AAT2_OUTBOUND/...).
 *
 * Sovrascrive normalizePath() per strippare anche il prefisso mount-path locale,
 * in modo che un path come "C:/Users/.../df-share/AAT5_OUTBOUND/..." diventi
 * "AAT5_OUTBOUND/..." automaticamente.
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

    /**
     * Prefisso mount-path normalizzato con forward slash per il confronto.
     * Es: "C:/Users/RiccardoRuscitto/Desktop/df-share"
     */
    private val mountPrefix: String = baseDir.toString().replace('\\', '/').trimEnd('/')

    init {
        logger.info("LocalStorageService initialized with mount path: {}", baseDir)

        if (!baseDir.toFile().exists()) {
            logger.warn("Mount path non trovato: {}. Ops deve montare la share.", baseDir)
        } else {
            logger.info("Mount path disponibile: {}", baseDir)
        }
    }

    /**
     * Sovrascrive normalizePath per gestire anche il prefisso mount-path locale.
     *
     * Gestisce tutti questi formati di input:
     *   \\zenit.aceaspa.it\Idrico_collaudo_DMS_DATI$\AAT2_OUTBOUND\...  → AAT2_OUTBOUND/...
     *   C:\Users\...\df-share\AAT5_OUTBOUND\...                         → AAT5_OUTBOUND/...
     *   C:/Users/.../df-share/AAT5_OUTBOUND/...                         → AAT5_OUTBOUND/...
     *   AAT2_OUTBOUND\subfolder\file.pdf                                → AAT2_OUTBOUND/subfolder/file.pdf
     *   null / blank                                                    → ""
     */
    override fun normalizePath(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) return ""

        // 1. Strip UNC root (\\server\share\...)
        var normalized = stripUncRoot(rawPath.trim())

        // 2. Uniforma separatori a forward slash
        normalized = normalized.replace('\\', '/').replace(Regex("/+"), "/").trim('/')

        // 3. Strip mount-path locale se presente come prefisso
        //    Es: "C:/Users/.../df-share/AAT5_OUTBOUND/..." → "AAT5_OUTBOUND/..."
        if (normalized.startsWith(mountPrefix, ignoreCase = true)) {
            normalized = normalized.removePrefix(mountPrefix).trimStart('/')
        }
        // Prova anche senza drive letter (caso in cui il path inizia con il nome della cartella di mount)
        val mountDirName = baseDir.fileName?.toString() ?: ""
        if (mountDirName.isNotBlank() && normalized.startsWith("$mountDirName/", ignoreCase = true)) {
            normalized = normalized.removePrefix("$mountDirName/")
        }

        return normalized.trim('/')
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

            // Tenta ATOMIC_MOVE, fallback a copy+delete se cross-device
            try {
                if (overwrite) {
                    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } else {
                    Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE)
                }
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                logger.debug("ATOMIC_MOVE non supportato, fallback copy+delete: {} -> {}", src, dst)
                if (overwrite) {
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.copy(src, dst)
                }
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
     * prefissi UNC o mount-path locale tramite normalizePath().
     *
     * Garantisce che il path risolto resti dentro la baseDir (path traversal check).
     */
    private fun resolveAndValidatePath(relativePath: String?): Path {
        val normalized = normalizePath(relativePath)

        val resolved = baseDir.resolve(normalized).normalize()

        if (!resolved.startsWith(baseDir)) {
            throw IllegalArgumentException("Path traversal non permesso: $relativePath -> $resolved")
        }

        return resolved
    }
}
