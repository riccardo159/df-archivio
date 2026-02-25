package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.InfoGeneriche
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InfoGenericheRepository : JpaRepository<InfoGeneriche, Long> {

    fun findByDcsId(dcsId: String): InfoGeneriche?
}
