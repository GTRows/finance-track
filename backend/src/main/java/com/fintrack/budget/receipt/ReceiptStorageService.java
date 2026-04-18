package com.fintrack.budget.receipt;

import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Persists transaction receipt files under a per-user directory tree. Stored
 * paths are relative to the configured root so the database never sees an
 * absolute host path — makes it safe to move the storage volume between
 * deployments without rewriting rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> MIME_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf"
    );

    private final TransactionRepository txnRepo;

    @Value("${fintrack.receipt-dir:/var/lib/fintrack/receipts}")
    private String rootDir;

    private Path root;

    @PostConstruct
    void init() {
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Receipt storage root: {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create receipt storage root " + root, e);
        }
    }

    public record StoredReceipt(String relativePath, String mimeType, long bytes) {
    }

    @Transactional
    public StoredReceipt store(UUID userId, UUID txnId, MultipartFile file) throws IOException {
        BudgetTransaction txn = txnRepo.findByIdAndUserId(txnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Empty receipt upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Receipt exceeds 5MB limit");
        }
        String mime = normalizeMime(file.getContentType());
        String ext = MIME_EXTENSIONS.get(mime);
        if (ext == null) {
            throw new IllegalArgumentException("Unsupported receipt type: " + file.getContentType());
        }

        if (txn.getReceiptPath() != null) {
            deleteFile(txn.getReceiptPath());
        }

        String relative = userId + "/" + txnId + "." + ext;
        Path target = safeResolve(relative);
        Files.createDirectories(target.getParent());
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        txn.setReceiptPath(relative);
        txnRepo.save(txn);
        log.info("Stored receipt for txn {} ({} bytes, {})", txnId, file.getSize(), mime);
        return new StoredReceipt(relative, mime, file.getSize());
    }

    public Loaded load(UUID userId, UUID txnId) throws IOException {
        BudgetTransaction txn = txnRepo.findByIdAndUserId(txnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        String relative = txn.getReceiptPath();
        if (relative == null || relative.isBlank()) {
            throw new ResourceNotFoundException("No receipt attached");
        }
        Path file = safeResolve(relative);
        if (!Files.exists(file)) {
            throw new ResourceNotFoundException("Receipt file missing on disk");
        }
        return new Loaded(Files.readAllBytes(file), detectMime(relative));
    }

    @Transactional
    public void delete(UUID userId, UUID txnId) {
        BudgetTransaction txn = txnRepo.findByIdAndUserId(txnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        String relative = txn.getReceiptPath();
        if (relative == null) return;
        deleteFile(relative);
        txn.setReceiptPath(null);
        txnRepo.save(txn);
    }

    public record Loaded(byte[] bytes, String mimeType) {
    }

    private void deleteFile(String relative) {
        try {
            Files.deleteIfExists(safeResolve(relative));
        } catch (IOException e) {
            log.warn("Failed to delete receipt {}: {}", relative, e.getMessage());
        }
    }

    private Path safeResolve(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Receipt path escapes storage root");
        }
        return resolved;
    }

    private String detectMime(String relative) {
        int dot = relative.lastIndexOf('.');
        String ext = dot >= 0 ? relative.substring(dot + 1).toLowerCase() : "";
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private String normalizeMime(String raw) {
        if (raw == null) return "";
        int semi = raw.indexOf(';');
        return (semi >= 0 ? raw.substring(0, semi) : raw).trim().toLowerCase();
    }
}
