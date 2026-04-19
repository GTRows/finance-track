package com.fintrack.analytics.benchmark;

import com.fintrack.analytics.benchmark.dto.BenchmarkSeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/benchmarks")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @GetMapping
    public ResponseEntity<BenchmarkSeriesResponse> fetch(
            @RequestParam(defaultValue = "365") int days) {
        return ResponseEntity.ok(benchmarkService.fetch(days));
    }
}
