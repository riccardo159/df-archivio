package com.acea.digitaflow.dfarchivio.schema

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

@Component
class SchemaMultiTenantConnectionProvider(
    private val dataSource: DataSource,
    @Value("\${spring.flyway.default-schema:aat2}")
    private val defaultSchema: String
) : MultiTenantConnectionProvider<String> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getAnyConnection(): Connection {
        return dataSource.connection
    }


    override fun releaseAnyConnection(connection: Connection) {
        connection.close()
    }

    override fun getConnection(tenantIdentifier: String): Connection {
        log.info("Using tenant/schema={}", tenantIdentifier)
        val connection = getAnyConnection()
        connection.createStatement().use {
            it.execute("""SET search_path TO "$tenantIdentifier"""")
        }
        return connection
    }

    override fun releaseConnection(tenantIdentifier: String, connection: Connection) {
        connection.createStatement().use {
            it.execute("""SET search_path TO "$defaultSchema"""")
        }
        connection.close()
    }

    override fun supportsAggressiveRelease(): Boolean = false

    override fun isUnwrappableAs(unwrapType: Class<*>): Boolean = false

    override fun <X : Any> unwrap(unwrapType: Class<X>): X {
        throw UnsupportedOperationException("Unwrap not supported")
    }
}
