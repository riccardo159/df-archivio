package com.acea.digitaflow.dfarchivio

import com.acea.digitaflow.dfarchivio.service.SmbService
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("smb-test")
class SmbTestRunner(
    private val smbService: SmbService
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        println("=== Avvio Test SMB ===")

        try {
            smbService.withShare { share ->
                println("✓ Connessione riuscita!")
                println("Share path: ${share.smbPath}")

                val files = share.list("")
                println("Totale elementi: ${files.size}")

                files.take(10).forEach { file ->
                    val type = if (file.fileAttributes and 0x10 != 0L) "[DIR]" else "[FILE]"
                    println("  $type ${file.fileName}")
                }
            }

            println("✓ Test completato con successo!")

        } catch (e: Exception) {
            println("✗ Test fallito!")
            e.printStackTrace()
        }
}}