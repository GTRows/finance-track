package com.fintrack.budget.allocation;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.budget.allocation.AllocationDtos.BucketListRequest;
import com.fintrack.budget.allocation.AllocationDtos.BucketResponse;
import com.fintrack.budget.allocation.AllocationDtos.PreviewRequest;
import com.fintrack.budget.allocation.AllocationDtos.PreviewResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/budget/allocation")
@RequiredArgsConstructor
public class AllocationController {

    private final CashFlowAllocatorService service;

    @GetMapping("/buckets")
    public ResponseEntity<List<BucketResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.listBuckets(user.getId()));
    }

    @PutMapping("/buckets")
    public ResponseEntity<List<BucketResponse>> replace(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody BucketListRequest request) {
        return ResponseEntity.ok(service.replaceBuckets(user.getId(), request.buckets()));
    }

    @PostMapping("/preview")
    public ResponseEntity<PreviewResponse> preview(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody PreviewRequest request) {
        return ResponseEntity.ok(service.preview(user.getId(), request));
    }
}
