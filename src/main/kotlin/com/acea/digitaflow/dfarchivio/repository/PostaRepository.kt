package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.Posta
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostaRepository : JpaRepository<Posta, Long> {

    fun findByInfoGenericheDcsId(dcsId: String): Posta?

}
