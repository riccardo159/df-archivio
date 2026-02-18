package com.acea.digitaflow.dfarchivio.config

data class SmbProperties(
    var server: String = "zenit.aceaspa.it",
    var share: String = "Idrico_collaudo_DMS_DATI$",
    var basePath: String = "aat2_OUTBOUND/FLUSSI_STAMPATORE/COMPUNET",
    var username: String = "",
    var password: String = "",
    var domain: String = "",
    var port: Int = 1445,
    var connectionTimeout: Int = 600000,
    var soTimeout: Int = 600000
)