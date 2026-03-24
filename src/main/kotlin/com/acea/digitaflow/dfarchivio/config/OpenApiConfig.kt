package com.acea.digitaflow.dfarchivio.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "Archivio API",
        version = "v1"
    )
)
class OpenApiConfig
