package com.acea.digitaflow.dfarchivio

import com.acea.digitaflow.dfarchivio.security.ApiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EntityScan(basePackages = ["com.acea.digitalflow.df_common.entity"])
@EnableConfigurationProperties(ApiProperties::class)
class DfArchivioApplication

fun main(args: Array<String>) {
    runApplication<DfArchivioApplication>(*args)
}
