package com.acea.digitaflow.dfarchivio.config

import com.acea.digitaflow.dfarchivio.security.LoggerInterceptor
import com.acea.digitaflow.dfarchivio.security.SocietaInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val loggerInterceptor: LoggerInterceptor,
    private val societaInterceptor: SocietaInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(societaInterceptor)
            .addPathPatterns("/**")
            .order(0)

        registry.addInterceptor(loggerInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/swagger-resources/**",
                "/v2/api-docs",
                "/webjars/**",
                "/error"
            )
    }
}
