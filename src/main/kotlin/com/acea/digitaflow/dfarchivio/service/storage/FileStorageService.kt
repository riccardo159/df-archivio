package com.acea.digitaflow.dfarchivio.service.storage

import com.acea.digitalflow.df_common.dto.StorageHealthStatus


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
     * Riporta diagnostica sullo stato dello storage
     */
    fun getHealthStatus(): StorageHealthStatus

    fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean): Boolean

    fun buildPath(relativeDir: String?): String {
        return if (relativeDir.isNullOrBlank()) ""
        else relativeDir.trim('/')
    }
}
