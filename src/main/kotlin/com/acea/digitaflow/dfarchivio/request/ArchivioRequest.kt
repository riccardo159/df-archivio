package com.acea.digitaflow.dfarchivio.request

data class ArchivioRequest(
    var dcsId: Long,
    var categoriaStampa: String,
    var nomeFileDms: String,
    var canaleSecondario: String? = null,
    var tipoCanaleSecondario: String,
    var sapDocId: String? = null,
    var nomeFileEnti: String,
    var dmsDestPath: String? = null,
    var esitoSdi: Int,
    var societa : String,
) {
}