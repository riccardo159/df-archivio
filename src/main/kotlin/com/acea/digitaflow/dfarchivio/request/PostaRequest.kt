package com.acea.digitaflow.dfarchivio.request

data class PostaRequest(
    var lotto: String? = null,
    var dcsId: String,
    var objectId: String? = null,
    var nomeFilePdf: String? = null,
    var codiceRaccomandataPosteItaliane: String? = null,
    var idBusta: String? = null,
    var idChannel: String? = null,
    var idDocType: String? = null,
    var archDocId: String? = null,
    var area: String? = null,
    var descrizione: String? = null
)
