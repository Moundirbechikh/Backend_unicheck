package com.example.presence_server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                    "http://localhost:3000",                         // Pour tes tests en local
                    "https://unicheck-drab.vercel.app",
                    "https://cron-job.org/"               // Ton site Vercel (Prod)

                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true) // Indispensable pour que le frontend envoie le header Authorization
                .maxAge(3600);
    }
}