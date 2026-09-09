package com.red.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 用于HTTP客户端调用
 */
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient.Builder webClient() {
        return WebClient.builder();
    }
}
