package com.fbcorp.gleo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TicketSessionMvcConfig implements WebMvcConfigurer {

    private final TicketSessionInterceptor ticketSessionInterceptor;

    public TicketSessionMvcConfig(TicketSessionInterceptor ticketSessionInterceptor) {
        this.ticketSessionInterceptor = ticketSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ticketSessionInterceptor)
                .addPathPatterns(
                        "/e/*/**"
                )
                .excludePathPatterns(
                        "/e/*/ticket",
                        "/e/*/ticket/**",
                        "/css/**",
                        "/js/**"
                );
    }
}
