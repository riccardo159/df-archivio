package com.acea.digitaflow.dfarchivio

import com.acea.digitaflow.dfarchivio.producer.NotificationProducerService
import com.acea.digitalflow.df_common.dto.ArchivioNotificationRequest
import com.acea.digitalflow.df_common.dto.MailDto
import com.acea.digitalflow.df_common.enums.CanaleSecondario
import com.acea.digitalflow.df_common.enums.TipoDocumento
import com.acea.digitalflow.df_common.enums.TipoMail
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@Profile("kafka-test")
class KafkaTestSender(

    private val notificationProducerService: NotificationProducerService

) : CommandLineRunner {

    override fun run(vararg args: String?) {

        println("KAFKA TEST SENDER STARTED")
        val richiesta = ArchivioNotificationRequest(
            tipoCanaleSecondario = CanaleSecondario.MAIL,
            flgCanaleSecondario = true,
            tipoDocumento = TipoDocumento.FATTURA,
            datiDocumento = MailDto(
                id = null,
                lotto = "LOTTO-001",
                dcsId = "DCS-TEST-001",
                tipoMail = TipoMail.PEC,
                mittente = "noreply@acea.it",
                destinatario = "cliente@test.it",
                oggetto = "Invio fattura",
                codStato = null,
                descrizione = "Invio fattura cliente",
                codCliente = "CLI123",
                nomeFileEml = "mail.eml",
                sapXmlData = null,
                denominazioneCliente = "Mario Rossi",
                destinatariCc = null,
                nFatturaRif = "FAT-001",
                dataRicezioneSap = Instant.now(),
                success = true,
                error = null,
                societa = "aat5"
            ),
            societa = "aat5",
            dcsId = "6562321g00510000DOC_2026_001116",
            testo = "Test invio kafka",
            campoDifferito = null
        )

    //    notificationProducerService.inviaNotifica(listOf(richiesta))

        val doxxe = ArchivioNotificationRequest(
            tipoCanaleSecondario = CanaleSecondario.MAIL,
            flgCanaleSecondario = true,
            tipoDocumento = TipoDocumento.FATTURA,
            datiDocumento = null,
            societa = "aat5",
            dcsId = "6562321g00510000DOC_2026_001111",
            testo = "Test invio kafka",
            campoDifferito = "20"
        )
   //     notificationProducerService.inviaNotifica(listOf(doxxe))

        val stampa = ArchivioNotificationRequest(
            tipoCanaleSecondario = CanaleSecondario.MAIL,
            flgCanaleSecondario = true,
            tipoDocumento = TipoDocumento.FATTURA,
            datiDocumento = null,
            societa = "aat5",
            dcsId = "6562321g00510000DOC_2026_001111",
            testo = "Test invio kafka",
            campoDifferito = null
        )
        notificationProducerService.inviaNotifica(listOf(richiesta))

    }
}