package com.acea.digitaflow.dfarchivio.service

import com.acea.digitaflow.dfarchivio.config.SmbProperties
import com.acea.digitaflow.dfarchivio.repository.SmbConfigRepository
import org.springframework.stereotype.Service

@Service
class SmbConfigService(
    private val repo: SmbConfigRepository
) {

    fun getEffectiveConfig(configKey: String = "DEFAULT"): SmbProperties {
        val db = repo.findFromCommonSchema(configKey)
            .orElseThrow { IllegalStateException("SmbProperties non presente per key=$configKey (schema comune)") }

        require(db.enabled) { "SmbProperties disabilitata per key=$configKey" }

        return SmbProperties(
            server = db.server,
            port = db.port,
            share = db.share,
            basePath = db.basePath,
            username = db.username.orEmpty(),
            password = db.password.orEmpty(),
            domain = db.domain.orEmpty(),
            connectionTimeout = db.connectionTimeoutMs,
            soTimeout = db.soTimeoutMs
        )
    }

}

