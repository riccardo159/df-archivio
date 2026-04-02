package com.acea.digitaflow.dfarchivio.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class FatturaArchivioDto @JsonCreator constructor(
    @JsonProperty("dcsId")
    val dcsId: String,

    @JsonProperty("societa")
    val societa: String? = null,

    @JsonProperty("esitoSdi")
    val esitoSdi: Int,

    @JsonProperty("nomeFlusso")
    val nomeFlusso: String,

    @JsonProperty("dataEvento")
    val dataEvento: Instant
)
