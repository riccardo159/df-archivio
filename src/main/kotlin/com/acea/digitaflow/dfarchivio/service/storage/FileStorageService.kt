package com.acea.digitaflow.dfarchivio.service.storage


// ============================================
// ABSTRACTION LAYER - Storage Service Interface
// ============================================

/**
 * Interfaccia di astrazione per operazioni su storage remoto.
 * Permette di switchare tra SMB e FileSystem locale senza cambiar codice.
 *
 * Implementazioni:
 * - SmbStorageService (SMB Client via smbj)
 * - LocalStorageService (Filesystem locale)
 *
 * Configurazione:
 *   storage.type: smb # oppure: local
 *   storage.smb.server: zenit.aceaspa.it
 *   storage.local.mount-path: /mnt/archivio
 */
interface FileStorageService {

    fun listFiles(relativePath: String? = null): List<String>

    fun downloadFile(relativeDir: String? = null, fileName: String): ByteArray

    fun uploadFile(relativeDir: String? = null, fileName: String, content: ByteArray)

    fun fileExists(relativeDir: String?, fileName: String): Boolean

    fun ensureDirectory(relativeDir: String?)

    fun deleteFile(relativeDir: String? = null, fileName: String)

    fun deleteFileIfExists(relativeDir: String? = null, fileName: String)

    fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean): Boolean

    /**
     * Normalizza un path rimuovendo:
     * - Prefissi UNC legacy (\\zenit.aceaspa.it\Idrico_*_DMS_DATI$\...)
     * - Prefisso mount-path locale (se l'implementazione lo sovrascrive)
     * - Backslash → forward slash, slash doppi, slash iniziali/finali
     *
     * Risultato: path relativo pulito tipo "AAT2_OUTBOUND/MASSIVI/file.pdf"
     */
    fun normalizePath(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) return ""

        val stripped = stripUncRoot(rawPath.trim())
        return stripped
            .replace('\\', '/')
            .replace(Regex("/+"), "/")
            .trim('/')
    }

    /**
     * Combina segmenti di path in un unico path normalizzato.
     * Ignora segmenti null o blank.
     *
     * Es: combinePath("AAT2_OUTBOUND", "MASSIVI", "file.pdf")
     *     → "AAT2_OUTBOUND/MASSIVI/file.pdf"
     */
    fun combinePath(vararg segments: String?): String {
        return segments
            .filterNot { it.isNullOrBlank() }
            .joinToString("/") { normalizePath(it) }
            .replace(Regex("/+"), "/")
            .trim('/')
    }

    /**
     * Rimuove la radice UNC (server + share) da un path.
     * Es: \\zenit.aceaspa.it\Idrico_collaudo_DMS_DATI$\AAT2_OUTBOUND\... → AAT2_OUTBOUND\...
     */
    fun stripUncRoot(path: String): String {
        var normalized = path.trim()

        if (normalized.startsWith("\\\\") || normalized.startsWith("//")) {
            val sep = if (normalized.startsWith("\\\\")) "\\" else "/"
            var p = normalized.removePrefix(sep + sep)
            val firstSep = p.indexOf(sep)
            if (firstSep != -1) p = p.substring(firstSep + 1)
            val secondSep = p.indexOf(sep)
            if (secondSep != -1) p = p.substring(secondSep + 1)
            return p
        }

        val sharePattern = Regex("""Idrico_\w+_DMS_DATI\$[/\\]""")
        normalized = sharePattern.replace(normalized, "")

        return normalized
    }

}