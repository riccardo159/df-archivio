package com.acea.digitaflow.dfarchivio.request

import com.acea.digitalflow.df_common.enums.Canale
import java.time.Instant

data class EsitoArchiviazioneRequest(
    val societa: String,
    val canale: Canale? = null,
    val dcsId: String,
    val stato: Int,
    val dataEvento: Instant
)
