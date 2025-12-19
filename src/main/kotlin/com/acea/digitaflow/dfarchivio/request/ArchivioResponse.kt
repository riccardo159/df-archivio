package com.acea.digitaflow.dfarchivio.request

//todo modificare
data class ArchivioResponse(
    var lotto: String? = null,
    var dcsId: String? = null,
    var tipoMail: String? = null,
    var mittente: String? = null,
    var destinatario: String? = null,
    var oggetto: String? = null,
    var descrizione: String? = null,
    val codCliente: String? = null,
    val denomCliente: String? = null,
    val destinatariCC: String? = null,
    val nomefileEML: String? = null,
    val sapXmlData: String? = null,
) {
}