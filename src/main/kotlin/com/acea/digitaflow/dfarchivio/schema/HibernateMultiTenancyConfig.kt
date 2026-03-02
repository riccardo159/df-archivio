package com.acea.digitaflow.dfarchivio.schema

import org.hibernate.cfg.AvailableSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer

@Configuration
class HibernateMultiTenancyConfig(
    private val tenantResolver: SchemaTenantIdentifierResolver,
    private val connectionProvider: SchemaMultiTenantConnectionProvider
) {

    @Bean
    fun hibernatePropertiesCustomizer(): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { props ->
            props[AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER] = connectionProvider
            props[AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = tenantResolver
        }
}
