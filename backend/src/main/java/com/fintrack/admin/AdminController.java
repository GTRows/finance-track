package com.fintrack.admin;

import com.fintrack.admin.dto.AdminSettingResponse;
import com.fintrack.admin.dto.LogFileInfo;
import com.fintrack.admin.dto.LogStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Admin-only endpoints for log management and system monitoring.
 * All endpoints require ADMIN role (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminSettingRepository adminSettingRepository;

    @Value("${logging.file.path:/var/log/fintrack}")
    private String logDirectory;

    /** Lists all log files in the log directory. */
    @GetMapping("/logs")
    public ResponseEntity<List<LogFileInfo>> listLogFiles() throws IOException {
        Path logDir = Paths.get(logDirectory);
        if (!Files.exists(logDir)) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<LogFileInfo> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "fintrack*")) {
            for (Path path : stream) {
                files.add(new LogFileInfo(
                        path.getFileName().toString(),
                        Files.size(path),
                        Files.getLastModifiedTime(path).toInstant(),
                        path.toString().endsWith(".gz")
                ));
            }
        }
        files.sort(Comparator.comparing(LogFileInfo::lastModified).reversed());
        return ResponseEntity.ok(files);
    }

    /** Downloads or streams a specific log file. */
    @GetMapping("/logs/{filename}")
    public ResponseEntity<Resource> downloadLogFile(@PathVariable String filename) {
        Path filePath = Paths.get(logDirectory, filename);
        if (!Files.exists(filePath) || !filePath.getParent().equals(Paths.get(logDirectory))) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(filePath);
        String contentType = filename.endsWith(".gz") ? "application/gzip" : "text/plain";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    /** Returns log file statistics: total size, file count, oldest/newest. */
    @GetMapping("/logs/stats")
    public ResponseEntity<LogStatsResponse> logStats() throws IOException {
        Path logDir = Paths.get(logDirectory);
        if (!Files.exists(logDir)) {
            return ResponseEntity.ok(new LogStatsResponse(0, 0, null, null));
        }

        long totalSize = 0;
        int fileCount = 0;
        Instant oldest = null;
        Instant newest = null;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "fintrack*")) {
            for (Path path : stream) {
                totalSize += Files.size(path);
                fileCount++;
                Instant modified = Files.getLastModifiedTime(path).toInstant();
                if (oldest == null || modified.isBefore(oldest)) {
                    oldest = modified;
                }
                if (newest == null || modified.isAfter(newest)) {
                    newest = modified;
                }
            }
        }

        return ResponseEntity.ok(new LogStatsResponse(totalSize, fileCount, oldest, newest));
    }

    /** Deletes a specific log file. */
    @DeleteMapping("/logs/{filename}")
    public ResponseEntity<Void> deleteLogFile(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(logDirectory, filename);
        if (!Files.exists(filePath) || !filePath.getParent().equals(Paths.get(logDirectory))) {
            return ResponseEntity.notFound().build();
        }
        Files.delete(filePath);
        log.info("Admin deleted log file: {}", filename);
        return ResponseEntity.noContent().build();
    }

    /** SSE stream of live log lines (tail -f equivalent). */
    @GetMapping("/logs/live")
    public SseEmitter liveLogStream() {
        SseEmitter emitter = new SseEmitter(0L);
        Path logFile = Paths.get(logDirectory, "fintrack.log");

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final long[] lastPosition = {0};

        if (Files.exists(logFile)) {
            try {
                lastPosition[0] = Files.size(logFile);
            } catch (IOException e) {
                log.warn("Could not get initial log file size", e);
            }
        }

        executor.scheduleAtFixedRate(() -> {
            try {
                if (!Files.exists(logFile)) {
                    return;
                }
                long currentSize = Files.size(logFile);
                if (currentSize > lastPosition[0]) {
                    try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                        raf.seek(lastPosition[0]);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            emitter.send(SseEmitter.event().data(line));
                        }
                        lastPosition[0] = raf.getFilePointer();
                    }
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
                executor.shutdown();
            }
        }, 0, 1, TimeUnit.SECONDS);

        emitter.onCompletion(executor::shutdown);
        emitter.onTimeout(executor::shutdown);

        return emitter;
    }

    /** Triggers manual log cleanup (delete files older than specified days). */
    @PostMapping("/logs/cleanup")
    public ResponseEntity<Map<String, Integer>> cleanupLogs(@RequestParam(defaultValue = "90") int maxAgeDays)
            throws IOException {
        Path logDir = Paths.get(logDirectory);
        if (!Files.exists(logDir)) {
            return ResponseEntity.ok(Map.of("deletedFiles", 0));
        }

        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(maxAgeDays));
        int deleted = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "fintrack*")) {
            for (Path path : stream) {
                if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                    Files.delete(path);
                    deleted++;
                }
            }
        }

        log.info("Admin triggered log cleanup: deleted {} files older than {} days", deleted, maxAgeDays);
        return ResponseEntity.ok(Map.of("deletedFiles", deleted));
    }

    /** Returns all admin settings. */
    @GetMapping("/settings")
    public ResponseEntity<List<AdminSettingResponse>> getSettings() {
        List<AdminSettingResponse> settings = adminSettingRepository.findAll().stream()
                .map(s -> new AdminSettingResponse(s.getKey(), s.getValue(), s.getDescription(),
                        s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null))
                .toList();
        return ResponseEntity.ok(settings);
    }

    /** Updates an admin setting by key. */
    @PutMapping("/settings/{key}")
    public ResponseEntity<AdminSettingResponse> updateSetting(@PathVariable String key,
                                                               @RequestBody Map<String, String> body) {
        return adminSettingRepository.findById(key)
                .map(setting -> {
                    setting.setValue(body.get("value"));
                    adminSettingRepository.save(setting);
                    log.info("Admin updated setting: {} = {}", key, body.get("value"));
                    return ResponseEntity.ok(new AdminSettingResponse(
                            setting.getKey(), setting.getValue(), setting.getDescription(),
                            setting.getUpdatedAt() != null ? setting.getUpdatedAt().toString() : null));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
