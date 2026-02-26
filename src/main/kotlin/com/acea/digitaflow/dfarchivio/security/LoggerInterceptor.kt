package com.acea.digitaflow.dfarchivio.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.*

@Component
class LoggerInterceptor(
    private val apiProperties: ApiProperties
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(LoggerInterceptor::class.java)

    @Value("\${df-archivio.expected-api-key}")
    private lateinit var expectedApiKey: String


    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        log.info("[preHandle][${request}][${request.method}]${request.requestURI}${getParameters(request)}")

        val encodedToken = request.getHeader("Token")

        if (encodedToken.isNullOrBlank()) {
            log.warn("Access Denied: Missing Token header for request URI: ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Token missing")
            return false
        }

        val decodedToken: String? = try {
            String(Base64.getDecoder().decode(encodedToken))
        } catch (e: IllegalArgumentException) {
            log.warn("Failed to decode Token from Base64: ${e.message} for URI: ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Invalid Token format")
            return false
        }

        if (decodedToken.isNullOrBlank()) {
            log.warn("Access Denied: Decoded Token is empty for URI: ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Empty Token after decoding")
            return false
        }

        val parts = decodedToken.split(".")
        if (parts.size != 2) {
            log.warn("Access Denied: Token has incorrect number of parts. Expected 2, got ${parts.size} for URI: ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Invalid Token structure")
            return false
        }

        val usernameFromToken = parts[0]
        val apiKeyFromToken = parts[1]

        if (apiKeyFromToken != apiProperties.expectedApiKey) {
            log.warn("Access Denied: API Key in Token does not match expected for URI: ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Invalid API Key")
            return false
        }

        if (!apiProperties.allowedUsers.contains(usernameFromToken)) {
            log.warn("Access Denied: User '$usernameFromToken' in Token not in allowed list for URI: ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: User not allowed")
            return false
        }
        return true
    }

    private fun getParameters(request: HttpServletRequest): String {
        val posted = StringBuilder()
        val e: Enumeration<String>? = request.parameterNames
        if (e != null && e.hasMoreElements()) {
            posted.append("?")
        }
        while (e != null && e.hasMoreElements()) {
            if (posted.length > 1) {
                posted.append("&")
            }
            val curr = e.nextElement()
            posted.append("$curr=")
            if (curr.contains("password", ignoreCase = true)
                || curr.contains("pass", ignoreCase = true)
                || curr.contains("pwd", ignoreCase = true)
            ) {
                posted.append("*****")
            } else {
                posted.append(request.getParameter(curr))
            }
        }
        val ip = request.getHeader("X-FORWARDED-FOR")
        val ipAddr: String = ip ?: getRemoteAddr(request)
        if (ipAddr.isNotBlank()) {
            posted.append("&_psip=$ipAddr")
        }
        return posted.toString()
    }

    private fun getRemoteAddr(request: HttpServletRequest): String {
        val ipFromHeader = request.getHeader("X-FORWARDED-FOR")
        if (!ipFromHeader.isNullOrBlank()) {
            log.debug("ip from proxy - X-FORWARDED-FOR : $ipFromHeader")
            return ipFromHeader
        }
        return request.remoteAddr
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        @Nullable ex: java.lang.Exception?
    ) {
        ex?.printStackTrace()
        log.info("[afterCompletion][$request][exception: $ex]")
    }

}
