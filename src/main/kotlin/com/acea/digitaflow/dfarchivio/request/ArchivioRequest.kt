package com.acea.digitaflow.dfarchivio.request

import com.acea.digitalflow.df_common.enums.Canale

data class ArchivioRequest(
    var dcsId: String,
    var categoriaStampa: String,
    var nomeFileDms: String,
    var canaleSecondario: String? = null,
    var tipoCanaleSecondario: String,
    var sapDocId: String? = null,
    var nomeFileEnti: String,
    var dmsDestPath: String? = null,
    var esitoSdi: Int,
    var societa: String,
    var canale: Canale? = null,
)