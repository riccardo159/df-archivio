package com.acea.digitaflow.dfarchivio.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "smb")
data class SmbProperties(
    var server: String = "zenit.aceaspa.it",
    var share: String = "Idrico_collaudo_DMS_DATI$",
    var basePath: String = "aat2_OUTBOUND/FLUSSI_STAMPATORE/COMPUNET",
    var username: String = "",
    var password: String = "",
    var domain: String = ""
)
