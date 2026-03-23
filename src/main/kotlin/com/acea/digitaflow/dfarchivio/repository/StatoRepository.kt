package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.Stato
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StatoRepository : JpaRepository<Stato, Long> {

    fun findByDcsIdAndTipologiaStatoCodStato(
        dcsId: String,
        codStato: Long
    ): List<Stato>

    fun findByDcsIdAndFlagAttivoTrue(dcsId: String): List<Stato>
}
