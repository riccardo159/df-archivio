package com.acea.digitaflow.dfarchivio.service.storage

import com.acea.digitaflow.dfarchivio.config.SmbProperties
import com.acea.digitaflow.dfarchivio.repository.SmbConfigRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SmbConfigService(
    private val repo: SmbConfigRepository,
    @Value("\${ZENIT_SMB_AMBIENTE:collaudo}")
    private val ambiente: String
) {

    fun getEffectiveConfig(configKey: String = "DEFAULT"): SmbProperties {
        val db = repo.findFromCommonSchema(configKey)
            .orElseThrow { IllegalStateException("SmbProperties non presente per key=$configKey (schema comune)") }

        require(db.enabled) { "SmbProperties disabilitata per key=$configKey" }
        val shareResolved = "Idrico_${ambiente}_DMS_DATI$"

        return SmbProperties(
            server = db.server,
            port = db.port,
            share = shareResolved,
            basePath = db.basePath,
            username = db.username.orEmpty(),
            password = db.password.orEmpty(),
            domain = db.domain.orEmpty(),
            connectionTimeout = db.connectionTimeoutMs,
            soTimeout = db.soTimeoutMs
        )
    }

}

