package com.acea.digitaflow.dfarchivio.request

import java.time.Instant

data class EsitoArchiviazioneRequest(
    val societa: String,
    val canale: String,
    val dcsId: String,
    val stato: Int,
    val dataEvento: Instant
)
