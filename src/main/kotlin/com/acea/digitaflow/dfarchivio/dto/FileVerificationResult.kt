package com.acea.digitaflow.dfarchivio.dto

/**
 * Risultato della verifica di un singolo file
 */
data class FileVerificationResult(
    /**
     * Tipo di documento verificato (DMS, PA, RDI, PDF, TXT, STAMPA)
     */
    val tipoDocumento: String,
    
    /**
     * Nome del file verificato
     */
    val nomeFile: String?,
    
    /**
     * Path completo del file verificato
     */
    val pathCompleto: String,
    
    /**
     * True se il file è stato trovato, false altrimenti
     */
    val trovato: Boolean,
    
    /**
     * Messaggio di errore se il file non è stato trovato o c'è stato un errore
     */
    val messaggioErrore: String? = null,
    
    /**
     * Tipo di errore (se presente)
     */
    val tipoErrore: TipoErroreFile? = null
)

/**
 * Tipi di errore possibili durante la verifica file
 */
enum class TipoErroreFile {
    FILE_NON_TROVATO,
    PATH_NON_ACCESSIBILE,
    PERMISSION_DENIED,
    TIMEOUT,
    NFS_NON_MONTATO,
    ERRORE_IO,
    FILE_IS_DIRECTORY
}

