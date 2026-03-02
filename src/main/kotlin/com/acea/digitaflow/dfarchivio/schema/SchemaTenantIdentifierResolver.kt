package com.acea.digitaflow.dfarchivio.schema

import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.stereotype.Component

@Component
class SchemaTenantIdentifierResolver :
    CurrentTenantIdentifierResolver<String> {

    override fun resolveCurrentTenantIdentifier(): String =
        TenantContext.get() ?: "aat2"

    /**
     * Ritorna false per invalidare le sessioni esistenti quando cambia il tenant identifier.
     * Questo è necessario quando cambiamo schema durante l'esecuzione nello stesso thread,
     * ad esempio quando processiamo batch di documenti per società diverse.
     * Con false, Hibernate chiude le sessioni esistenti e ne crea di nuove con lo schema corretto
     * quando il tenant identifier cambia.
     */
    override fun validateExistingCurrentSessions(): Boolean = false
}

object TenantContext {
    private val current = ThreadLocal<String>()

    fun set(schema: String) = current.set(schemaFromCompany(schema))
    fun get(): String? = current.get()
    fun clear() = current.remove()
}

fun schemaFromCompany(company: String): String =
    company.lowercase()
