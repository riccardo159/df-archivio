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

    /**
     * Elenca file in una cartella
     *
     * @param relativePath Percorso relativo alla base configurata
     * @return Lista di nomi file (senza . e ..)
     */
    fun listFiles(relativePath: String? = null): List<String>

    /**
     * Scarica un file e ritorna il contenuto come ByteArray
     *
     * @param relativeDir Cartella relativa
     * @param fileName Nome del file
     * @return Contenuto del file
     */
    fun downloadFile(relativeDir: String? = null, fileName: String): ByteArray

    /**
     * Upload di un file (crea o sovrascrive)
     *
     * @param relativeDir Cartella relativa
     * @param fileName Nome del file
     * @param content Contenuto da scrivere
     */
    fun uploadFile(relativeDir: String? = null, fileName: String, content: ByteArray)

    /**
     * Controlla se un file esiste
     */
    fun fileExists(relativeDir: String?, fileName: String): Boolean

    /**
     * Crea ricorsivamente una directory se non esiste
     */
    fun ensureDirectory(relativeDir: String?)

    /**
     * Elimina un file
     */
    fun deleteFile(relativeDir: String? = null, fileName: String)

    /**
     * Elimina un file solo se esiste (non lancia eccezione se non esiste)
     */
    fun deleteFileIfExists(relativeDir: String? = null, fileName: String)

    /**
     * Sposta un file da sourcePath a destPath.
     * Entrambi i path sono relativi alla base configurata.
     *
     * @param sourcePath Path relativo del file sorgente
     * @param destPath Path relativo di destinazione
     * @param overwrite Se true, sovrascrive il file di destinazione se esiste
     * @return true se lo spostamento è avvenuto con successo
     */
    fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean): Boolean

    /**
     * Normalizza un path rimuovendo eventuali prefissi UNC legacy
     * e uniformando i separatori a forward slash.
     *
     * Trasforma:
     *   \\zenit.aceaspa.it\Idrico_*_DMS_DATI$\AAT2_OUTBOUND\... → AAT2_OUTBOUND/...
     *   AAT2_OUTBOUND\subfolder\file.pdf                        → AAT2_OUTBOUND/subfolder/file.pdf
     *   null / blank                                             → ""
     */
    fun normalizePath(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) return ""

        val stripped = stripUncRoot(rawPath.trim())
        return stripped
            .replace('\\', '/')       // uniforma separatori
            .replace(Regex("/+"), "/") // rimuove slash doppi
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
     * Se il path non è UNC, rimuove comunque il pattern della share se presente.
     */
    fun stripUncRoot(path: String): String {
        var normalized = path.trim()

        // Se è un UNC path (\\server\share\... oppure //server/share/...), rimuove server e share
        if (normalized.startsWith("\\\\") || normalized.startsWith("//")) {
            val sep = if (normalized.startsWith("\\\\")) "\\" else "/"
            var p = normalized.removePrefix(sep + sep)
            // Rimuove il server (es: zenit.aceaspa.it)
            val firstSep = p.indexOf(sep)
            if (firstSep != -1) p = p.substring(firstSep + 1)
            // Rimuove la share (es: Idrico_collaudo_DMS_DATI$)
            val secondSep = p.indexOf(sep)
            if (secondSep != -1) p = p.substring(secondSep + 1)
            return p
        }

        // Fallback: rimuove pattern Idrico_*_DMS_DATI$ se presente nel path
        val sharePattern = Regex("""Idrico_\w+_DMS_DATI\$[/\\]""")
        normalized = sharePattern.replace(normalized, "")

        return normalized
    }

}