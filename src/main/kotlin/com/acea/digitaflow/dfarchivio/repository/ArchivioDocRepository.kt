package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.Archivio
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ArchivioDocRepository : JpaRepository<Archivio, Long> {

    fun findByInfoGenericheDcsId(dcsId: String): Archivio?

}
