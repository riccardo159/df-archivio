package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.TipologiaStato
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TipologiaStatoRepository : JpaRepository<TipologiaStato, Long> {
    fun findByCodStato(codStato: Long): TipologiaStato?

    //937 generato archivio
}
