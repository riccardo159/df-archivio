package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.repository.ArchivioDocRepository
import com.acea.digitalflow.df_common.dto.ArchivioDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArchivioDocService(
    private val archivioDocRepository: ArchivioDocRepository
) {

    private val logger = LoggerFactory.getLogger(ArchivioDocService::class.java)

    fun buildDto(dcsId: String, societa: String): ArchivioDto? {
        val archivio = archivioDocRepository.findByInfoGenericheDcsId(dcsId)
        if (archivio == null) {
            logger.warn("Entity Archivio non trovata per dcsId={}", dcsId)
            return null
        }

        return ArchivioDto(
            dcsId = dcsId,
            lotto = archivio.lotto,
            objectId = archivio.objectId,
            nomeFilePdf = archivio.nomeFilePdf,
            idChannel = archivio.idChannel,
            idArea = archivio.idArea,
            societa = societa
        )
    }

}
