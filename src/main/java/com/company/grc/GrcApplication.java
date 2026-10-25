package com.company.grc;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class GrcApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(GrcApplication.class, args);
    }

    @PostConstruct
    void initTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        System.out.println("[GrcApplication] JVM timezone set to: " + TimeZone.getDefault().getID());
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);  // 15 seconds to establish connection
        factory.setReadTimeout(120_000);    // 120 seconds to read response for AI ops
        return new RestTemplate(factory);
    }

    @Bean
    public ApplicationRunner databaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                System.out.println("[GrcApplication] Verifying database schema for recent manual migrations...");
                // Add is_trashed column if Flyway was disabled
                jdbcTemplate.execute("ALTER TABLE gst_details ADD COLUMN IF NOT EXISTS is_trashed BOOLEAN DEFAULT FALSE;");

                // Part 2: Create clients and client_vendor_mappings tables if Flyway was disabled
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS clients (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "client_gstin VARCHAR(15) UNIQUE NOT NULL, " +
                        "client_name VARCHAR(255) NOT NULL, " +
                        "logo_data TEXT, " +
                        "logo_content_type VARCHAR(50), " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS client_vendor_mappings (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE, " +
                        "vendor_gstin VARCHAR(15) NOT NULL, " +
                        "mapped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "CONSTRAINT uq_vendor_gstin UNIQUE (vendor_gstin));");

                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_cvm_client_id ON client_vendor_mappings(client_id);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_cvm_vendor_gstin ON client_vendor_mappings(vendor_gstin);");

                // Ensure strict 1-to-many: one vendor mapped with one client only
                try {
                    jdbcTemplate.execute("ALTER TABLE client_vendor_mappings DROP CONSTRAINT IF EXISTS uq_client_vendor;");
                    jdbcTemplate.execute("DELETE FROM client_vendor_mappings a USING client_vendor_mappings b WHERE a.id < b.id AND a.vendor_gstin = b.vendor_gstin;");
                    jdbcTemplate.execute("ALTER TABLE client_vendor_mappings DROP CONSTRAINT IF EXISTS uq_vendor_gstin;");
                    jdbcTemplate.execute("ALTER TABLE client_vendor_mappings ADD CONSTRAINT uq_vendor_gstin UNIQUE (vendor_gstin);");
                } catch (Exception ex) {
                    System.err.println("[GrcApplication] uq_vendor_gstin migration notice: " + ex.getMessage());
                }

                System.out.println("[GrcApplication] Database schema verification successful.");
            } catch (Exception e) {
                System.err.println("[GrcApplication] Failed to run manual database migration: " + e.getMessage());
            }
        };
    }
}
