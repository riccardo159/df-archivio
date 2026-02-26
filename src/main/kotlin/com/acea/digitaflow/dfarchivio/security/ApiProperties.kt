package com.acea.digitaflow.dfarchivio.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "df-archivio")
data class ApiProperties(
    var expectedApiKey: String = "",
    var allowedUsers: List<String> = emptyList()
)
