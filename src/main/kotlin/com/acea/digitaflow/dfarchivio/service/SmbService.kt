package com.acea.digitaflow.dfarchivio.service

import com.hierynomus.smbj.share.DiskShare
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
class SmbService(
    @Value("\${storage.local.path:/df-share}")
    private val localBasePath: String
) {
    private val logger = LoggerFactory.getLogger(SmbService::class.java)
    private val baseDir: Path = Paths.get(localBasePath).toAbsolutePath()

    init {
        logger.info("SmbService initialized with mount path: {}", localBasePath)

        // Verifica che la mount esiste
        if (!baseDir.toFile().exists()) {
            logger.warn("⚠️  Mount path non trovato: {}. Ops deve montare la share.", baseDir)
            logger.warn("Comando: sudo mount -t cifs //zenit/Idrico_pro_DMS_DATI$ {} \\", localBasePath)
            logger.warn("  -o username=testuser,password=testpass")
        } else {
            logger.info("✓ Mount path disponibile: {}", baseDir)
        }
    }

    private fun resolveLocalPath(relativePath: String?): Path {
        val base = Path.of(localBasePath)
        return if (relativePath.isNullOrBlank()) base
        else base.resolve(relativePath.trim('/'))
    }

    fun moveFileLocal(sourcePath: String, destPath: String, overwrite: Boolean): Boolean {
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


    fun buildPath(relativeDir: String?): String {
        return if (relativeDir.isNullOrBlank())""
        else relativeDir.trim('/')
    }

}
