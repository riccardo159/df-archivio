package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.repository.MailRepository
import com.acea.digitalflow.df_common.dto.MailDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MailService(
    private val mailRepository: MailRepository
) {

    private val logger = LoggerFactory.getLogger(MailService::class.java)

    fun buildDto(dcsId: String, societa: String): MailDto? {
        val mail = mailRepository.findByDcsId(dcsId)
        if (mail == null) {
            logger.warn("Entity Mail non trovata per dcsId={}", dcsId)
            return null
        }

        return MailDto(
            id = mail.id,
            lotto = mail.lotto,
            dcsId = mail.dcsId,
            tipoMail = mail.tipoMail,
            mittente = mail.mittente,
            destinatario = mail.destinatario,
            oggetto = mail.oggetto,
            codCliente = mail.codCliente,
            nomeFileEml = mail.nomefileEml,
            denominazioneCliente = mail.denominazioneCliente,
            destinatariCc = mail.destinatariCc,
            nFatturaRif = mail.nFatturaRif,
            dataRicezioneSap = mail.dataRicezioneSap,
            societa = societa
        )
    }

}
