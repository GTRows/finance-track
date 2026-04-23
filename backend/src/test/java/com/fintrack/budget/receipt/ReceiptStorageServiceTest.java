package com.fintrack.budget.receipt;

import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptStorageServiceTest {

    @Mock TransactionRepository txnRepo;

    @InjectMocks ReceiptStorageService service;

    @TempDir Path tempDir;

    private final UUID userId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "rootDir", tempDir.toString());
        service.init();
    }

    private BudgetTransaction txn(String receiptPath) {
        return BudgetTransaction.builder()
                .id(txnId).userId(userId)
                .txnType(TxnType.EXPENSE)
                .amount(new BigDecimal("10"))
                .txnDate(LocalDate.now())
                .receiptPath(receiptPath)
                .build();
    }

    @Test
    void storeRejectsMissingTransaction() {
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("f", "r.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.store(userId, txnId, file))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void storeRejectsEmptyUpload() {
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(txn(null)));
        MockMultipartFile empty = new MockMultipartFile("f", "r.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.store(userId, txnId, empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty");
    }

    @Test
    void storeRejectsFilesOverFiveMegabytes() {
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(txn(null)));
        byte[] big = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("f", "r.jpg", "image/jpeg", big);

        assertThatThrownBy(() -> service.store(userId, txnId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void storeRejectsUnsupportedMimeType() {
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(txn(null)));
        MockMultipartFile file = new MockMultipartFile("f", "r.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> service.store(userId, txnId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void storeWritesFileUnderUserDirectoryAndStampsTransaction() throws IOException {
        BudgetTransaction existing = txn(null);
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(existing));
        byte[] body = "hello".getBytes();
        MockMultipartFile file = new MockMultipartFile("f", "r.png", "image/png", body);

        ReceiptStorageService.StoredReceipt res = service.store(userId, txnId, file);

        assertThat(res.relativePath()).isEqualTo(userId + "/" + txnId + ".png");
        assertThat(res.mimeType()).isEqualTo("image/png");
        assertThat(res.bytes()).isEqualTo(body.length);
        Path onDisk = tempDir.resolve(res.relativePath());
        assertThat(Files.exists(onDisk)).isTrue();
        assertThat(Files.readAllBytes(onDisk)).isEqualTo(body);
        assertThat(existing.getReceiptPath()).isEqualTo(res.relativePath());
        verify(txnRepo).save(existing);
    }

    @Test
    void storeAcceptsMimeTypeWithCharsetParameter() throws IOException {
        BudgetTransaction existing = txn(null);
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(existing));
        MockMultipartFile file = new MockMultipartFile(
                "f", "r.pdf", "application/pdf; charset=utf-8", new byte[]{1, 2});

        ReceiptStorageService.StoredReceipt res = service.store(userId, txnId, file);

        assertThat(res.mimeType()).isEqualTo("application/pdf");
        assertThat(res.relativePath()).endsWith(".pdf");
    }

    @Test
    void storeReplacesExistingReceiptOnDisk() throws IOException {
        String oldRelative = userId + "/" + txnId + ".jpg";
        Path oldFile = tempDir.resolve(oldRelative);
        Files.createDirectories(oldFile.getParent());
        Files.writeString(oldFile, "old");

        BudgetTransaction existing = txn(oldRelative);
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(existing));
        MockMultipartFile newFile = new MockMultipartFile("f", "r.png", "image/png", "new".getBytes());

        ReceiptStorageService.StoredReceipt res = service.store(userId, txnId, newFile);

        assertThat(Files.exists(oldFile)).isFalse();
        Path stored = tempDir.resolve(res.relativePath());
        assertThat(Files.readString(stored)).isEqualTo("new");
    }

    @Test
    void loadReadsFileContentsAndMime() throws IOException {
        String relative = userId + "/" + txnId + ".webp";
        Path onDisk = tempDir.resolve(relative);
        Files.createDirectories(onDisk.getParent());
        Files.writeString(onDisk, "data");
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(txn(relative)));

        ReceiptStorageService.Loaded loaded = service.load(userId, txnId);

        assertThat(new String(loaded.bytes())).isEqualTo("data");
        assertThat(loaded.mimeType()).isEqualTo("image/webp");
    }

    @Test
    void loadThrowsWhenTransactionHasNoReceiptAttached() {
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(txn(null)));

        assertThatThrownBy(() -> service.load(userId, txnId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No receipt");
    }

    @Test
    void loadThrowsWhenFileMissingOnDisk() {
        when(txnRepo.findByIdAndUserId(txnId, userId))
                .thenReturn(Optional.of(txn(userId + "/" + txnId + ".jpg")));

        assertThatThrownBy(() -> service.load(userId, txnId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void deleteRemovesFileAndClearsTransactionReceiptPath() throws IOException {
        String relative = userId + "/" + txnId + ".pdf";
        Path onDisk = tempDir.resolve(relative);
        Files.createDirectories(onDisk.getParent());
        Files.writeString(onDisk, "bytes");
        BudgetTransaction existing = txn(relative);
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(existing));

        service.delete(userId, txnId);

        assertThat(Files.exists(onDisk)).isFalse();
        assertThat(existing.getReceiptPath()).isNull();
        verify(txnRepo).save(existing);
    }

    @Test
    void deleteIsNoOpWhenReceiptPathNull() {
        BudgetTransaction existing = txn(null);
        when(txnRepo.findByIdAndUserId(txnId, userId)).thenReturn(Optional.of(existing));

        service.delete(userId, txnId);

        verify(txnRepo, never()).save(any());
    }

    @Test
    void loadRejectsPathThatEscapesRoot() {
        when(txnRepo.findByIdAndUserId(txnId, userId))
                .thenReturn(Optional.of(txn("../../../etc/passwd")));

        assertThatThrownBy(() -> service.load(userId, txnId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
    }
}
