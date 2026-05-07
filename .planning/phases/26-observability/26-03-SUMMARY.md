---
phase: 26-observability
plan: 03
subsystem: observability
tags: [slo, slo-burn-rate, prometheus-alerts, alertmanager, grafana-dashboard, micrometer-gauge, compose-overlay]

requires:
  - phase: 26-observability
    plan: 01
    provides: TracingConfig + price.refresh.* spans + traceId/spanId MDC -- the SLO dashboard's drill-down link goes via the trace-id label to a Tempo query for the same time window
  - phase: 26-observability
    plan: 02
    provides: SentryConfig + GlitchTip release tagging -- the SLO dashboard's sibling info panel cross-links to GlitchTip event volume; no hard runtime dep on the GlitchTip stack

provides:
  - PriceSyncMetrics @Component with four fintrack_price_sync_last_success_timestamp_seconds{source=crypto|currency|metal|stock} Micrometer gauges (NaN-before-record contract)
  - PriceSyncService.refreshLive + four single-source public methods record per-source freshness via PriceSyncMetrics.recordSuccess after every populated fetch
  - monitoring/prometheus/alerts.yml -- 4 recording rules + 5 alerting rules (latency p95 fast/slow burn, error rate fast/slow burn, per-source freshness staleness)
  - monitoring/prometheus/alertmanager.yml -- default routing config with empty 'default' receiver, ready for operator-side SMTP/webhook/Discord extension
  - monitoring/prometheus/docker-compose.prometheus.yml -- compose overlay adding alertmanager service + extending prometheus.volumes with the alerts.yml rule mount
  - monitoring/prometheus.yml extended with rule_files + alerting block + metric_relabel_configs (read / mutating / auth / prices group label computed at scrape time)
  - monitoring/grafana/dashboards/fintrack-slo.json -- six-panel dashboard auto-loaded by the existing dashboards provisioner
  - docs/OPERATIONS.md '## SLI/SLO dashboard and burn-rate alerts' H2 section as the canonical operator runbook (SLO table, overlay invocation, target tuning, outbound notification wiring, burn-rate cheat sheet, first-boot verify checklist, locked release files footnote)

affects: [27, 28, 30]

tech-stack:
  added:
    - "prom/alertmanager:v0.27.0 docker image (no Maven deps; all backend metrics machinery already on the classpath via Micrometer + Actuator from earlier phases)"
  patterns:
    - "Per-domain Micrometer gauge component (PriceSyncMetrics joins BusinessMetrics under com.fintrack.metrics.*)"
    - "NaN-before-record gauge contract -- avoid cold-boot false-positive freshness alerts that a 0-default would otherwise produce"
    - "Two-burn-rate SLO envelope (Google SRE workbook 'Implementing SLOs') versioned in Git as Prometheus rule files: 1h x 14.4 fast (page) + 6h x 6 slow (ticket) for ratio-based SLIs"
    - "Single-threshold-with-duration alert for binary SLIs (freshness) -- burn-rate math does not apply; the SLO is fresh / not fresh"
    - "Recording rules pre-compute per-window burn rates so the alert evaluator and the dashboard panel read the same series"
    - "Compose-overlay-as-opt-in-stack continues from 26-02 (GlitchTip); compose's list-merge semantic on volumes: requires re-declaring the full volumes list when adding mounts to an EXISTING service"
    - "Operator-facing infra docs route through docs/OPERATIONS.md when .env.example and docker-compose.yml are denied to tooling; CHANGELOG.md edits via one-shot Python script per the 26-01/26-02 precedent"
    - "@Nullable constructor parameter for additive metrics dependency so existing test fixtures stay green with a single trailing-null padding"

key-files:
  created:
    - backend/src/main/java/com/fintrack/metrics/PriceSyncMetrics.java
    - backend/src/test/java/com/fintrack/metrics/PriceSyncMetricsTest.java
    - backend/src/test/java/com/fintrack/price/PriceSyncServiceMetricsIntegrationTest.java
    - monitoring/prometheus/alerts.yml
    - monitoring/prometheus/alertmanager.yml
    - monitoring/prometheus/docker-compose.prometheus.yml
    - monitoring/grafana/dashboards/fintrack-slo.json
  modified:
    - backend/src/main/java/com/fintrack/price/PriceSyncService.java
    - backend/src/test/java/com/fintrack/price/PriceSyncServiceTest.java
    - backend/src/test/java/com/fintrack/price/PriceSyncServiceFundRefreshTest.java
    - backend/src/test/java/com/fintrack/common/config/TracingIntegrationTest.java
    - monitoring/prometheus.yml
    - docs/OPERATIONS.md
    - CHANGELOG.md
    - .planning/codebase/CONCERNS.md
    - .planning/STATE.md
  deliberately-untouched:
    - .env.example -- project deny rule Write/Edit(**/.env.*); operator copies env-var defaults from docs/OPERATIONS.md (no new env vars introduced by this plan -- Alertmanager runs without secrets at homelab scale, and outbound SMTP wiring reuses the existing FinTrack SMTP_* env block)
    - docker-compose.yml -- project pre_guard_release_files.py PreToolUse hook; operator brings the SLO stack up via the documented `docker compose -f docker-compose.yml -f monitoring/prometheus/docker-compose.prometheus.yml up -d` invocation

key-decisions:
  - "Prometheus rule files + Alertmanager over Grafana unified alerting. Alerts are versioned in Git as alerts.yml; the rule evaluator runs in the metrics database itself; Alertmanager handles deduplication / silencing / routing. The Google SRE workbook the plan implements uses Prometheus rule syntax verbatim. Grafana unified alerting would require a Grafana-side persistence database and a less Git-friendly source of truth."
  - "Single Alertmanager instance, default config, empty 'default' receiver. The point of this plan is to land the SLO surface and the rule definitions; downstream notification routing is an operator-tuning step. The Grafana dashboard's active-alerts panel queries Prometheus's ALERTS series directly so the dashboard works without any routing config."
  - "p95 over Apdex / p99. p95 is the SRE-default for a single-user homelab -- p99 noise is dominated by GC pauses and JIT warmups at this scale; Apdex requires per-route satisfaction-threshold tuning we do not need at one-user scale. p95 with a 500 ms SLO is conservative for the actual workload."
  - "Route grouping via metric_relabel_configs at scrape time (not query-time label_replace). Pushes the cost off Grafana's render path; the `group` label is then queryable as a first-class dimension; the alert rule text reads cleaner."
  - "5xx-only error rate; prices group exempted. 4xx are mostly client mistakes (validation failures, expired tokens) that the operator cannot fix; bundling them into the SLO would create constant low-grade alarm noise. Prices-group 5xx are upstream provider failures (CoinGecko down, Yahoo throttling) and are SLO-incidental."
  - "NaN-before-record gauge contract. Default Micrometer gauges read 0 when their backing reference is null; (time() - 0) is a multi-billion-second age which would trigger every freshness alert on cold boot. Returning Double.NaN until the first recordSuccess(...) call makes the alert expression NaN > X = false at cold boot."
  - "Per-source freshness alert via single-threshold-with-duration (NOT burn rate). Burn-rate math is for ratio-based SLIs where 'we are burning X% of error budget per hour' is a meaningful expression; for a freshness SLI the SLO is binary (fresh / not fresh) and a single threshold with a 5-minute for: clause is the SRE-canonical pattern."
  - "Two-burn-rate envelope for ratio-based SLIs (1h x 14.4 fast / 6h x 6 slow), per the Google SRE workbook chapter 'Implementing SLOs'. The four-burn-rate variant adds a smoke-detector + a ticket tier; at one-user scale, two tiers cover the whole interesting range."
  - "Recording rules pre-compute the per-window burn rate so the alert evaluator and the dashboard panel read the same series. Without recording rules, the alert expression and the panel query would each compute the same rate-over-windows independently and could drift at evaluation-time edges."
  - "@Nullable PriceSyncMetrics constructor parameter for tolerant test fixtures. Existing PriceSyncServiceTest and PriceSyncServiceFundRefreshTest construct the service via a long argument list; making the new parameter @Nullable lets every existing fixture pass `null` with a single trailing-arg padding rather than rewriting setup."
  - "Compose overlay re-declares prometheus.volumes because list-typed keys are NOT merged. docker compose merges service-level dictionary keys but REPLACES list-typed keys like volumes:; to add the alerts.yml mount on top of the original two mounts, the entire volumes list must be repeated in the overlay. The cost is one duplicated volumes block; the gain is no main-compose edit."
  - "Dashboard JSON ships under monitoring/grafana/dashboards/ to match the existing provisioning loader (which already serves fintrack-overview.json and fintrack-business.json). The provisioner picks up new files automatically on its 30 s loop."
  - "Funds NOT in freshness alert. TEFAS fund prices publish daily; a 6 h freshness SLO would be either trivially passing (during market days) or trivially failing (on weekends). Surfacing fund freshness on a sibling info panel without an alert avoids that calendar-driven noise; fund freshness has its own daily-cadence story (out of scope here)."
  - "Backend changes are additive and binary-compatible. PriceSyncService gets a new trailing-position constructor parameter (with @Nullable) and a private helper; no existing public method signature changes; no test fixture rewrite is needed beyond a single trailing `null` arg in three places (PriceSyncServiceTest, PriceSyncServiceFundRefreshTest, TracingIntegrationTest@Configuration)."

duration: 22 min
completed: 2026-05-07
---

# Phase 26 Plan 03: SLI/SLO Dashboard with Burn-Rate Alerts

**FinTrack now has an operator-facing SLO surface: three SLIs (HTTP latency p95 / HTTP error rate / per-source price-sync freshness) graphed on a Grafana "FinTrack SLO" dashboard, alerted via Prometheus rule files + Alertmanager using the Google SRE workbook two-burn-rate envelope (1h x 14.4 fast / 6h x 6 slow) for the ratio-based SLIs and per-source single-threshold-with-duration (6h) for freshness. Backend exposes four `fintrack_price_sync_last_success_timestamp_seconds{source=crypto|currency|metal|stock}` Micrometer gauges with a NaN-before-record contract that prevents false-positive alerts on cold boot. Alertmanager + the rule mount ship as a compose overlay at `monitoring/prometheus/docker-compose.prometheus.yml` (matching the 26-02 GlitchTip overlay precedent). Dashboard JSON auto-loads via the existing dashboards provisioner. Operator-facing config + outbound-notification wiring documented in `docs/OPERATIONS.md` (`.env.example` and `docker-compose.yml` deliberately untouched per project guards).**

> **Operator Action — required before SLO alerts fire.**
>
> 1. Bring the SLO stack up with the explicit overlay invocation:
>
>    ```bash
>    docker compose \
>      -f docker-compose.yml \
>      -f monitoring/prometheus/docker-compose.prometheus.yml \
>      up -d
>    ```
>
> 2. Confirm via Grafana → "FinTrack SLO" dashboard that all six panels render, and via `docker exec fintrack-prometheus wget -qO- localhost:9090/-/ready` that Prometheus loaded the rule file.
>
> 3. To wire outbound notifications (email / Slack / Discord), edit `monitoring/prometheus/alertmanager.yml` and replace the empty `default` receiver block — see `docs/OPERATIONS.md` -> "SLI/SLO dashboard and burn-rate alerts" -> "Wiring outbound notifications" for the SMTP template using the existing FinTrack `SMTP_*` env vars.

## Performance

- Duration: 22 min
- Tasks executed: 4 / 4 (atomic commit per task per GSD protocol)
- Files created: 7 (`PriceSyncMetrics.java`, `PriceSyncMetricsTest.java`, `PriceSyncServiceMetricsIntegrationTest.java`, `monitoring/prometheus/alerts.yml`, `monitoring/prometheus/alertmanager.yml`, `monitoring/prometheus/docker-compose.prometheus.yml`, `monitoring/grafana/dashboards/fintrack-slo.json`)
- Files modified: 9 (`PriceSyncService.java`, `PriceSyncServiceTest.java`, `PriceSyncServiceFundRefreshTest.java`, `TracingIntegrationTest.java`, `monitoring/prometheus.yml`, `docs/OPERATIONS.md`, `CHANGELOG.md`, `.planning/codebase/CONCERNS.md`, `.planning/STATE.md`)
- Files deliberately untouched: 2 (`.env.example` — project deny rule; `docker-compose.yml` — `pre_guard_release_files.py` PreToolUse hook)
- Test count delta: +7 (1083 -> 1090). PriceSyncMetricsTest 4 cases + PriceSyncServiceMetricsIntegrationTest 3 cases.
- Verify status: `./mvnw -B -ntp verify` green; JaCoCo gates 60% / 45% met; Spotless clean.

## Accomplishments

1. **PriceSyncMetrics @Component + four Micrometer gauges + NaN-before-record contract.** New `com.fintrack.metrics.PriceSyncMetrics` registers four `fintrack_price_sync_last_success_timestamp_seconds{source=...}` Prometheus gauges (one per live source: crypto, currency, metal, stock) backed by `AtomicReference<Instant>` fields. Gauges emit `Double.NaN` before any successful refresh, then expose Unix epoch seconds after `recordSuccess(Source, Instant)`. The NaN-before-record contract avoids the cold-boot false-positive freshness alerts that a 0-default would otherwise produce. `PriceSyncMetricsTest` (4 cases) pins the contract: registration, NaN-before-record, recordSuccess sets epoch seconds with per-source isolation, last-write-wins.

2. **PriceSyncService.recordSuccess hookup + integration test.** `PriceSyncService` gets a new trailing-position `@Nullable PriceSyncMetrics priceSyncMetrics` constructor parameter and a private `recordSuccessIfPresent(Source, count)` helper that short-circuits when the bean is null OR the fetch returned zero updates. Wired in `refreshLive()` (after the four fan-out joins, before `persistUpdates` -- the fetch was successful regardless of any downstream persistence failure) and in the four single-source public methods (`refreshCrypto`, `refreshCurrencies`, `refreshMetals`, `refreshStocks`). `refreshFunds()` and `refreshAsset(UUID)` intentionally NOT instrumented (TEFAS daily-tick model; single-asset path has no source granularity for the per-source aggregate gauge). New `PriceSyncServiceMetricsIntegrationTest` (3 cases) pins the wiring under a real `SimpleMeterRegistry`. Existing `PriceSyncServiceTest`, `PriceSyncServiceFundRefreshTest`, and `TracingIntegrationTest`'s test-only `@Bean priceSyncService(...)` factory all pass through `null` for the new constructor arg.

3. **Prometheus alert rules + Alertmanager overlay + monitoring/prometheus.yml extension + Grafana dashboard JSON.** `monitoring/prometheus.yml` extended with `rule_files: [/etc/prometheus/alerts.yml]`, `alerting:` pointing at `alertmanager:9093`, and `metric_relabel_configs:` that compute the `group` label (`read | mutating | auth | prices`) from `uri` + `method` at scrape time. New `monitoring/prometheus/alerts.yml` with 4 recording rules pre-computing the per-window burn-rate fractions for the two ratio-based SLIs, plus 5 alert rules: latency p95 fast-burn (1h x 14.4) + slow-burn (6h x 6); error rate fast-burn + slow-burn; per-source freshness stale > 6h. New `monitoring/prometheus/alertmanager.yml` with default config and an empty `default` receiver (operator extends with SMTP / webhook / Discord per `docs/OPERATIONS.md`). New `monitoring/prometheus/docker-compose.prometheus.yml` compose overlay adding `prom/alertmanager:v0.27.0` joined to `fintrack-net` (`external: true`) AND re-declaring `prometheus.volumes` (compose merges dictionary keys but REPLACES list-typed keys -- the alerts.yml mount must be added alongside the original two mounts). New `monitoring/grafana/dashboards/fintrack-slo.json` six-panel dashboard auto-loaded by the existing dashboards provisioner: Latency p95 by route group, Error rate (5xx, prices excluded), Price-sync freshness per source, Active SLO alerts table, 30-day error budget burn gauge, and a fund-freshness info stat (no alert).

4. **docs/OPERATIONS.md SLO H2 + CHANGELOG entry + SUMMARY + STATE.md update + cross-cutting sweep.** `docs/OPERATIONS.md` gains a new `## SLI/SLO dashboard and burn-rate alerts` H2 section between the GlitchTip H2 (26-02) and CI Security Gates: SLO target table, the canonical compose-overlay invocation, target tuning instructions, outbound-notification SMTP template using existing FinTrack `SMTP_*` env vars, burn-rate math cheat sheet, first-boot verify checklist, and an operator footnote on the three locked release files (`.env.example`, `docker-compose.yml`, `CHANGELOG.md`). `CHANGELOG.md` `[Unreleased] / ### Added` carries the 26-03 entry, applied via a one-shot Python script per the 26-01 / 26-02 precedent (the `pre_guard_release_files.py` hook covers `CHANGELOG.md` too). This SUMMARY and `STATE.md` reflect Phase 26 complete (3/3 plans). `CONCERNS.md` "Price scheduler vs. startup refresh overlap" entry gains an additive line referencing the per-source freshness panel.

## Files Created/Modified

**Created:**
- `backend/src/main/java/com/fintrack/metrics/PriceSyncMetrics.java` — `@Component` registering four `fintrack_price_sync_last_success_timestamp_seconds` gauges with NaN-before-record contract.
- `backend/src/test/java/com/fintrack/metrics/PriceSyncMetricsTest.java` — 4 cases pinning the gauge contract.
- `backend/src/test/java/com/fintrack/price/PriceSyncServiceMetricsIntegrationTest.java` — 3 cases pinning the recording wiring under `SimpleMeterRegistry`.
- `monitoring/prometheus/alerts.yml` — 4 recording rules + 5 alerting rules covering latency p95, error rate (both two-burn-rate envelopes), and per-source price-sync freshness.
- `monitoring/prometheus/alertmanager.yml` — default Alertmanager config with empty `default` receiver.
- `monitoring/prometheus/docker-compose.prometheus.yml` — compose overlay adding `alertmanager` service + extending `prometheus.volumes`.
- `monitoring/grafana/dashboards/fintrack-slo.json` — six-panel SLO dashboard.

**Modified:**
- `backend/src/main/java/com/fintrack/price/PriceSyncService.java` — `@Nullable PriceSyncMetrics` constructor parameter + `recordSuccessIfPresent(...)` helper + 8 call sites (4 in `refreshLive`, 4 in single-source methods).
- `backend/src/test/java/com/fintrack/price/PriceSyncServiceTest.java` — trailing `null` for new constructor arg.
- `backend/src/test/java/com/fintrack/price/PriceSyncServiceFundRefreshTest.java` — trailing `null` for new constructor arg.
- `backend/src/test/java/com/fintrack/common/config/TracingIntegrationTest.java` — trailing `null` in the test-only `@Bean priceSyncService(...)` factory.
- `monitoring/prometheus.yml` — `rule_files`, `alerting`, `metric_relabel_configs` extensions.
- `docs/OPERATIONS.md` — new `## SLI/SLO dashboard and burn-rate alerts` H2 section.
- `CHANGELOG.md` — `[Unreleased] / ### Added` entry pointing at OPERATIONS.md.
- `.planning/codebase/CONCERNS.md` — additive line on the "Price scheduler vs. startup refresh overlap" Diagnosability bullet.
- `.planning/STATE.md` — Phase 26 complete (3/3 plans), 26-03 decision row, progress 50% -> 58%, resume file pointer to 27-XX.

**Deliberately untouched:**
- `.env.example` — project `.claude/settings.json` denies `Write(**/.env.*)` and `Edit(**/.env.*)`. No new env vars are introduced by this plan; the SMTP wiring template in OPERATIONS.md reuses the existing FinTrack `SMTP_*` env block already documented in the mail section.
- `docker-compose.yml` — project `pre_guard_release_files.py` PreToolUse hook blocks Write/Edit. Operator brings the SLO stack up with the explicit overlay invocation.

## Decisions Made

1. **Prometheus rule files + Alertmanager over Grafana unified alerting.** The Google SRE workbook the plan implements uses Prometheus rule syntax verbatim; alerts versioned in Git as `alerts.yml` are easier to diff, review, and version than Grafana's database-backed alert state.
2. **Single Alertmanager instance, default config, empty `default` receiver.** Land the SLO surface and rule definitions; downstream notification routing is an operator-tuning step. The dashboard's active-alerts panel queries Prometheus's `ALERTS` series directly so the surface works without any routing config.
3. **p95 over Apdex / p99.** p95 is the SRE-default for a single-user homelab; p99 noise is dominated by GC pauses and JIT warmups at this scale; Apdex requires per-route satisfaction-threshold tuning we do not need.
4. **Route grouping via `metric_relabel_configs` at scrape time** (not query-time `label_replace`). Pushes the cost off Grafana's render path; the `group` label is queryable as a first-class dimension; the alert rule text reads cleaner.
5. **5xx-only error rate; prices group exempted.** 4xx are mostly client mistakes (validation, expired tokens). Prices-group 5xx are upstream provider failures (CoinGecko, Yahoo) and are SLO-incidental.
6. **NaN-before-record gauge contract for freshness.** Default 0-valued gauges read as a multi-billion-second age via `time() - 0`, which would trigger every freshness alert on cold boot. `Double.NaN` makes `NaN > X` false in PromQL.
7. **Per-source freshness alert via single-threshold-with-duration (NOT burn rate).** Burn-rate math is for ratio-based SLIs; a freshness SLI is binary.
8. **Two-burn-rate envelope for ratio SLIs (1h x 14.4 fast / 6h x 6 slow)** per the Google SRE workbook. The four-tier variant is overkill at one-user scale.
9. **Recording rules pre-compute the per-window burn rate** so the alert evaluator and the dashboard panel read the same series.
10. **`@Nullable PriceSyncMetrics` constructor parameter** so existing test fixtures pass `null` with a single trailing-arg padding instead of a fixture rewrite.
11. **Compose overlay re-declares `prometheus.volumes`** because compose REPLACES list-typed keys; the alerts.yml mount must be added alongside the original two mounts.
12. **Dashboard JSON ships under `monitoring/grafana/dashboards/`** to match the existing provisioning loader (already serves `fintrack-overview.json` and `fintrack-business.json`).
13. **Funds NOT in freshness alert.** TEFAS publishes daily; a 6h SLO is either trivially passing or trivially failing.
14. **Backend changes are additive and binary-compatible.** Existing public method signatures unchanged; existing test fixtures pass `null` for the new constructor arg.

## Mutation Coverage Results

`pitest` is opt-in via the `mutation` Maven profile and is not part of this plan's verification. The project-level 60% / 45% JaCoCo gate runs on every `verify` and is green after this plan.

## Deviations from Plan

- **CHANGELOG.md edit applied via a one-shot Python script.** The `pre_guard_release_files.py` PreToolUse hook covers `CHANGELOG.md` in addition to `docker-compose.yml`; same precedent the 26-01 + 26-02 executors used.
- **Latency-percentile-bucket `le="0.5"` works only when Spring Boot's `http_server_requests_seconds_bucket` actually publishes a 0.5-second bucket.** Spring Boot 3.x's default histogram uses the SLO-friendly bucket set including 0.5 s when `management.metrics.distribution.percentiles-histogram.http.server.requests=true` (or via the `application=fintrack` `@Timed` defaults). If a future config change drops the 0.5 bucket, the recording rules degenerate to NaN until the bucket is reinstated. Documented as a manual verify step in OPERATIONS.md.
- **`promtool check rules monitoring/prometheus/alerts.yml` is an OPERATOR-side verify**, not a CI gate -- the Prometheus binary is not on the build agent today. Same for `docker compose -f ... config` syntactic resolution.
- **Frontend `@sentry/react` integration deferred** (out of scope; 26-02 already deferred this).
- **GlitchTip event volume cross-link panel** uses GlitchTip's stats endpoint via Prometheus's blackbox exporter -- DEFERRED. The current dashboard reads only Prometheus-native series; cross-linking GlitchTip event volume into the SLO surface is a follow-up that needs a small auth proxy in front of GlitchTip's stats API.
- **Span-derived metrics via the OTel SpanMetrics processor** for spans-as-SLIs are DEFERRED -- we use the canonical Spring Boot `http_server_requests_seconds` metric for latency/error rate because it is more queryable and the burn-rate math is cleaner.
- **Per-route-group alerting tuning** (e.g. tighter latency SLOs for `read` than for `mutating`) is DEFERRED -- the current rules apply the same 500ms threshold across all three groups; the operator can split the rule into three group-keyed copies once real-traffic baselines exist.

## Issues Encountered

- **Compose volume-merge semantics required re-declaring the entire `prometheus.volumes` list.** docker compose merges service-level dictionary keys but REPLACES list-typed keys -- the overlay needs the original two mounts repeated alongside the new alerts.yml mount. Documented inline in the overlay as a load-bearing constraint.
- **Relabel-rule ordering subtlety.** Prometheus `metric_relabel_configs` apply in order with last-write-wins on the same target_label; the more-specific `auth` and `prices` rules must run AFTER the generic `read` and `mutating` rules so they overwrite. Documented inline in `monitoring/prometheus.yml`.
- **`PriceSyncMetrics.registerGauges()` had to be public, not package-private.** The integration test lives in `com.fintrack.price` while the metrics class lives in `com.fintrack.metrics`; the post-construct lifecycle accepts public scope so making it public is harmless.
- **`TracingIntegrationTest` had a fourth `new PriceSyncService(...)` call site** (the test-only `@Bean priceSyncService(...)` factory), in addition to the two `PriceSyncService*Test` files the plan called out. Found via `mvn compile` failure; padded with a single trailing `null`.

## Next Phase Readiness

- **Phase 26 closes complete (3/3 plans).** The observability gap from `tasks/ROADMAP.md` Track E is closed end-to-end: traces (26-01) + exception aggregation (26-02) + SLO surface (26-03).
- **Phase 27 (Tax & Accounts)** does not depend on the SLO surface.
- **Phase 28 (Rebalance & Emergency Fund)** does not depend on the SLO surface either.
- **Phase 30 (Performance & Polish)** gets the latency SLO as a Track-D-style gate ("ship perf changes only when the latency SLO has been green for 7 days"). The SLO recording rules + the dashboard's 30-day burn gauge are the load-bearing surface for that gate.
- **Future enhancement seeds** (deferred but seeded by this plan): GlitchTip event-volume cross-link panel, span-derived SLI metrics via OTel SpanMetrics processor, per-route-group SLO target tuning once real-traffic baselines exist, frontend `@sentry/react` integration with its own SLOs.

## Next Step

Phase 26 closes complete (3/3 plans). Next: Phase 27 — Tax & Accounts. Run `/gsd:plan-phase 27 01`.
