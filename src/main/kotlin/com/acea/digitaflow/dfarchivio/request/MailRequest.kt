package com.acea.digitaflow.dfarchivio.request

data class MailRequest(
    var id: Long? = null,
    var lotto: String? = null,
    var dcsId: String? = null,
    var tipoMail: String? = null,
    var mittente: String? = null,
    var destinatario: String? = null,
    var oggetto: String? = null,
    var descrizione: String? = null,
    val codCliente: String? = null,
    val nomeFileEml: String? = null,
    var denominazioneCliente: String? = null,
    var destinatariCc: String? = null,
    var nFatturaRif: String? = null,
    var success: Boolean? = null,
    var error: String? = null
)
