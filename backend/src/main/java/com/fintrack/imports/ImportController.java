package com.fintrack.imports;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.imports.dto.ImportSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final ExcelImportService importService;

    @PostMapping("/excel/preview")
    public ResponseEntity<ImportSummary> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(importService.preview(file));
    }

    @PostMapping("/excel/commit")
    public ResponseEntity<ImportSummary> commit(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(importService.commit(user.getId(), file));
    }
}
