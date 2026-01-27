package com.acea.digitaflow.dfarchivio

import com.acea.digitaflow.dfarchivio.service.SmbService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
@EntityScan(basePackages = ["com.acea.digitalflow.df_common.entity"])
class DfArchivioApplication{
    @Bean
    fun startupRunner(smbService: SmbService) = CommandLineRunner {
        try {
            println("=== Inizio scansione SMB ===")
            val files = smbService.listFiles()
            println("File trovati: ${files.size}")
            files.forEach { println("Trovato file SMB: $it") }
            println("=== Scansione SMB completata ===")
        } catch (e: Exception) {
            println("? ERRORE SMB: ${e.message}")
            e.printStackTrace()
            // NON rilanciare l'eccezione per non crashare l'app
        }
    }
}

fun main(args: Array<String>) {
    runApplication<DfArchivioApplication>(*args)
}
