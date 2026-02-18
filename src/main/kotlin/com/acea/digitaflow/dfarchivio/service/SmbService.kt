package com.acea.digitaflow.dfarchivio.service

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SmbService(
    private val configProvider: SmbConfigService
) {

    private val logger = LoggerFactory.getLogger(SmbService::class.java)

    /**
     * Restituisce una sessione SMB e la DiskShare per la share configurata.
     * Usa un `use` annidato in ogni metodo pubblico.
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
                // connection verrà chiusa quando SMBClient viene chiuso, ma chiudiamola esplicitamente
                runCatching { connection.close() }
            }
        }
    }


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

    /**
     * Lista i file nella directory base (COMPUNET) o in una sotto directory relativa.
     */
    fun listFiles(share: DiskShare, relativeDir: String? = null): List<String> {
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

    private fun isPathNotFound(e: Exception): Boolean =
        e is com.hierynomus.mssmb2.SMBApiException &&
                (e.message?.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true) == true ||
                        e.message?.contains("0xc000003a", ignoreCase = true) == true)


    /**
     * Costruisce il path completo relativo alla share, partendo da basePath.
     */
    fun buildPath(relativeDir: String?): String {
        val props = configProvider.getEffectiveConfig("DEFAULT")
        return if (relativeDir.isNullOrBlank()) props.basePath.trimEnd('/')
        else props.basePath.trimEnd('/') + "/" + relativeDir.trim('/')
    }

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

    fun fileExists(share: DiskShare, fullPath: String): Boolean {
        val normalized = normalizeSmbPath(fullPath)
        logger.debug("Check SMB exists: {}", normalized)
        return share.fileExists(normalized)
    }

    private fun normalizeSmbPath(path: String): String =
        path.trim().trimStart('/', '\\').replace('/', '\\')


}
