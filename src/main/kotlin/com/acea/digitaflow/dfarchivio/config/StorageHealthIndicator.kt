package com.acea.digitaflow.dfarchivio.config

import com.acea.digitaflow.dfarchivio.service.storage.FileStorageService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class StorageHealthIndicator(
    private val fileStorageService: FileStorageService
) : HealthIndicator {

    override fun health(): Health {
        val status = fileStorageService.getHealthStatus()

        return if (status.isConnected) {
            Health.up()
                .withDetail("storageType", status.storageType)
                .withDetail("basePath", status.basePath)
                .withDetail("lastConnectionTime", status.lastConnectionTime)
                .build()
        } else {
            Health.down()
                .withDetail("storageType", status.storageType)
                .withDetail("basePath", status.basePath)
                .withDetail("error", status.errorMessage ?: "unknown")
                .build()
        }
    }
}