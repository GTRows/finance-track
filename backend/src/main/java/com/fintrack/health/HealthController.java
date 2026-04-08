package com.fintrack.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enhanced health endpoint providing system component status.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    /** Returns detailed system health including database, Redis, and JVM status. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("version", "1.0.0");
        result.put("uptime", formatUptime());
        result.put("timestamp", Instant.now().toString());

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("database", checkDatabase());
        components.put("redis", checkRedis());
        result.put("components", components);

        return ResponseEntity.ok(result);
    }

    /** Returns JVM and system metrics for the admin panel. */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> system() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        jvm.put("heapMaxMb", runtime.maxMemory() / (1024 * 1024));
        jvm.put("availableProcessors", runtime.availableProcessors());
        jvm.put("uptime", formatUptime());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jvm", jvm);
        result.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(result);
    }

    private String checkDatabase() {
        try {
            dataSource.getConnection().close();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkRedis() {
        try {
            redisConnectionFactory.getConnection().close();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String formatUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration d = Duration.ofMillis(uptimeMs);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        }
        return String.format("%dh %dm", hours, minutes);
    }
}
