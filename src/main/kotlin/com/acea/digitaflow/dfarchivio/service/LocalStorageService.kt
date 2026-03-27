package com.acea.digitaflow.dfarchivio.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
class LocalStorageService(
    @Value("\${storage.local.path:/df-share}")
    private val localBasePath: String,
    @Value("\${ZENIT_SMB_AMBIENTE:collaudo}")
    private val ambiente: String,
) {

    private val logger = LoggerFactory.getLogger(LocalStorageService::class.java)
    private val baseDir: Path = Paths.get(localBasePath).toAbsolutePath()

    val shareResolved = "Idrico_${ambiente}_DMS_DATI$"

    init {
        logger.info("SmbService initialized with mount path: {}", localBasePath)

        // Verifica che la mount esiste
        if (!baseDir.toFile().exists()) {
            logger.warn("Mount path non trovato: {}. Ops deve montare la share.", baseDir)
        } else {
            logger.info("Mount path disponibile: {}", baseDir)
        }
    }

    private fun resolveLocalPath(relativePath: String?): Path {
        val base = Path.of(localBasePath, shareResolved)
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

    fun buildPath(relativeDir: String?): String {
        return if (relativeDir.isNullOrBlank()) ""
        else relativeDir.trim('/')
    }

}
