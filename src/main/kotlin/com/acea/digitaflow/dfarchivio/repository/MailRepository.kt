package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.Mail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MailRepository : JpaRepository<Mail, Long> {

    fun findByDcsId(dcsId: String): Mail?

}
