package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.InfoGeneriche
import com.acea.digitalflow.df_common.enums.CanaleSecondario
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InfoGenericheRepository : JpaRepository<InfoGeneriche, Long> {

    fun findByDcsId(dcsId: String): InfoGeneriche?

}
