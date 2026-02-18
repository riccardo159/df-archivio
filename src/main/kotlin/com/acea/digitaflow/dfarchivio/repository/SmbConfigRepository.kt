package com.acea.digitaflow.dfarchivio.repository

import com.acea.digitalflow.df_common.entity.SmbConfigEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface SmbConfigRepository : JpaRepository<SmbConfigEntity, String>{
    @Query(
        value = "SELECT * FROM df_common_config.smb_config WHERE config_key = :key",
        nativeQuery = true
    )
    fun findFromCommonSchema(@Param("key") key: String): Optional<SmbConfigEntity>

}
