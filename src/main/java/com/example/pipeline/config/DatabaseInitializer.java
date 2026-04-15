package com.example.pipeline.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseInitializer {
    /**
     * Creates baseline tables required by pipeline output on application startup.
     */
    @Bean
    public CommandLineRunner initDb(JdbcTemplate jdbcTemplate) {
        return args -> {
            // Ensure default destination table exists for quick-start pipelines.
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS processed_customers (id VARCHAR(255), name_upper VARCHAR(255), age INT, tenant_id VARCHAR(255))");
        };
    }
}
