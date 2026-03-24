package com.acea.digitaflow.dfarchivio

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication

@SpringBootApplication
@EntityScan(basePackages = ["com.acea.digitalflow.df_common.entity"])
class DfArchivioApplication

fun main(args: Array<String>) {
    runApplication<DfArchivioApplication>(*args)
}