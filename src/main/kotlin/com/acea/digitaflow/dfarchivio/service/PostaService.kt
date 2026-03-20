package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.repository.PostaRepository
import com.acea.digitalflow.df_common.dto.PostaDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PostaService(
    private val postaRepository: PostaRepository
) {

    private val logger = LoggerFactory.getLogger(PostaService::class.java)

    fun buildDto(dcsId: String, societa: String): PostaDto? {
        val posta = postaRepository.findByInfoGenericheDcsId(dcsId)
        if (posta == null) {
            logger.warn("Entity Posta non trovata per dcsId={}", dcsId)
            return null
        }

        return PostaDto(
            lotto = posta.lotto,
            dcsId = dcsId,
            societa = societa,
            objectId = posta.objectId,
            nomeFilePdf = posta.nomeFilePdf,
            codiceRaccomandataPosteItaliane = posta.codiceRaccomandataPosteItaliane,
            idBusta = posta.idBusta,
            idChannel = posta.idChannel,
            idDocType = posta.idDocType,
            archDocId = posta.archDocId,
            area = posta.area,
            descrizione = posta.descrizioneScarto
        )
    }

}
