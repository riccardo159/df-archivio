package com.acea.digitaflow.dfarchivio.mapper

import com.acea.digitalflow.df_common.dto.ArchivioDto
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.dto.PostaDto
import com.acea.digitaflow.dfarchivio.dto.FatturaArchivioDto
import com.acea.digitalflow.df_common.enums.CanaleSecondario
import com.acea.digitalflow.df_common.enums.TipoDocumento
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named

@Mapper(componentModel = "spring")
interface NotificationMapper {

    @Mapping(target = "tipoCanaleSecondario", constant = "ARCHIVIO")
    @Mapping(target = "flgCanaleSecondario", constant = "true")
    @Mapping(target = "tipoDocumento", constant = "ARCHIVIAZIONE")
    @Mapping(target = "datiDocumento", source = "archivio")
    @Mapping(target = "societa", source = "archivio.societa")
    @Mapping(target = "dcsId", source = "archivio.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(archivio: ArchivioDto): ArchivioNotificationRequest

    @Mapping(target = "tipoCanaleSecondario", constant = "MAIL")
    @Mapping(target = "flgCanaleSecondario", constant = "true")
    @Mapping(target = "tipoDocumento", constant = "MAIL")
    @Mapping(target = "datiDocumento", source = "mail")
    @Mapping(target = "societa", source = "mail.societa")
    @Mapping(target = "dcsId", source = "mail.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(mail: MailDto): ArchivioNotificationRequest

    @Mapping(target = "tipoCanaleSecondario", constant = "POSTA")
    @Mapping(target = "flgCanaleSecondario", constant = "true")
    @Mapping(target = "tipoDocumento", constant = "POSTALE")
    @Mapping(target = "datiDocumento", source = "posta")
    @Mapping(target = "societa", source = "posta.societa")
    @Mapping(target = "dcsId", source = "posta.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(posta: PostaDto): ArchivioNotificationRequest

    @Mapping(target = "tipoCanaleSecondario", constant = "ARCHIVIO")
    @Mapping(target = "flgCanaleSecondario", constant = "true")
    @Mapping(target = "tipoDocumento", constant = "FATTURA")
    @Mapping(target = "datiDocumento", source = "fatturaArchivio")
    @Mapping(target = "societa", source = "fatturaArchivio.societa")
    @Mapping(target = "dcsId", source = "fatturaArchivio.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(fatturaArchivio: FatturaArchivioDto): ArchivioNotificationRequest
}
