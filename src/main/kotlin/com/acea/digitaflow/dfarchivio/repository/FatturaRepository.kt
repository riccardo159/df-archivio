package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.Fattura
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FatturaRepository : JpaRepository<Fattura, Long> {

    fun findByInfoGenericheDcsId(dcsId: String): Fattura?

}