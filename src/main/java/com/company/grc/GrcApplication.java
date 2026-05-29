package com.company.grc;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
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
}
