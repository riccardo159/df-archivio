package com.acea.digitaflow.dfarchivio.request

import com.acea.digitalflow.df_common.entity.DocumentiRecapito
import com.acea.digitalflow.df_common.entity.InfoGeneriche
import com.acea.digitalflow.df_common.entity.Stato

//todo modificare
data class ArchivioResponse(
    var id: Long? = null,
    var lotto: String? = null,
    var objectId: String? = null,
    var nomeFilePdf: String? = null,
    var idChannel: String? = null,
    var idArea: String? = null,
    var infoGeneriche: InfoGeneriche? = null,
    var documentiRecapito: DocumentiRecapito? = null,
    var codStato: Stato? = null,
) {
}