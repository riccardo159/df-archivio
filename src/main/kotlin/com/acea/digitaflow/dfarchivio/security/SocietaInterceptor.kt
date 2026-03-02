package com.acea.digitaflow.dfarchivio.security

import com.acea.digitaflow.dfarchivio.schema.TenantContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Interceptor che legge l'header X-Societa dalla richiesta HTTP
 * e imposta il TenantContext PRIMA che Spring gestisca la transazione.
 * Questo garantisce che quando Hibernate risolve il tenant identifier,
 * il TenantContext sia già impostato correttamente.
 */
@Component
class SocietaInterceptor : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(SocietaInterceptor::class.java)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val societa = request.getHeader("X-Societa")

        if (societa != null && societa.isNotBlank()) {
            TenantContext.clear()
            TenantContext.set(societa)
            val schema = TenantContext.get()
            log.info("Società impostata dall'header X-Societa: '{}' -> schema '{}'", societa, schema)
        } else {
            TenantContext.clear()
            TenantContext.set("aat2")
            log.debug("Header X-Societa non presente, uso default: aat2")
        }

        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        TenantContext.clear()
    }
}
