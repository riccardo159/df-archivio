package com.acea.digitaflow.dfarchivio.mapper

import com.acea.digitaflow.dfarchivio.dto.FatturaArchivioDto
import com.acea.digitalflow.df_common.dto.ArchivioDto
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.dto.PostaDto
import com.acea.digitalflow.df_common.entity.InfoGeneriche
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface NotificationMapper {

    @Mapping(target = "tipoCanaleSecondario", source = "infoGeneriche.canaleSecondario")
    @Mapping(target = "flgCanaleSecondario", source = "infoGeneriche.flgCanaleSecondario")
    @Mapping(target = "tipoDocumento", source = "infoGeneriche.docType")
    @Mapping(target = "datiDocumento", expression = "java(archivio)")
    @Mapping(target = "societa", source = "archivio.societa")
    @Mapping(target = "dcsId", source = "archivio.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(archivio: ArchivioDto, infoGeneriche: InfoGeneriche): ArchivioNotificationRequest

    // Per MailDto
    @Mapping(target = "tipoCanaleSecondario", source = "infoGeneriche.canaleSecondario")
    @Mapping(target = "flgCanaleSecondario", source = "infoGeneriche.flgCanaleSecondario")
    @Mapping(target = "tipoDocumento", source = "infoGeneriche.docType")
    @Mapping(target = "datiDocumento", expression = "java(mail)")
    @Mapping(target = "societa", source = "mail.societa")
    @Mapping(target = "dcsId", source = "mail.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(mail: MailDto, infoGeneriche: InfoGeneriche): ArchivioNotificationRequest

    // Per PostaDto
    @Mapping(target = "tipoCanaleSecondario", source = "infoGeneriche.canaleSecondario")
    @Mapping(target = "flgCanaleSecondario", source = "infoGeneriche.flgCanaleSecondario")
    @Mapping(target = "tipoDocumento", source = "infoGeneriche.docType")
    @Mapping(target = "datiDocumento", expression = "java(posta)")
    @Mapping(target = "societa", source = "posta.societa")
    @Mapping(target = "dcsId", source = "posta.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(posta: PostaDto, infoGeneriche: InfoGeneriche): ArchivioNotificationRequest

    @Mapping(target = "tipoCanaleSecondario", constant = "ARCHIVIO")
    @Mapping(target = "flgCanaleSecondario", constant = "true")
    @Mapping(target = "tipoDocumento", constant = "ARCHIVIAZIONE")
    @Mapping(target = "datiDocumento",  expression = "java(archivio)")
    @Mapping(target = "societa", source = "archivio.societa")
    @Mapping(target = "dcsId", source = "archivio.dcsId")
    @Mapping(target = "testo", ignore = true)
    @Mapping(target = "campoDifferito", ignore = true)
    fun toNotification(archivio: FatturaArchivioDto): ArchivioNotificationRequest

}
