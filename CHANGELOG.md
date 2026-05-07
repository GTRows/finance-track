# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Phase 26-03: SLI/SLO dashboard and burn-rate alerts. Three SLIs graphed and alerted on (HTTP latency p95 per route group, HTTP error rate over total request volume, per-source price-sync freshness). Two-burn-rate envelope (1h x 14.4 fast / 6h x 6 slow) per Google SRE workbook for the ratio-based SLIs; per-source single-threshold-with-duration (6h) for freshness. Alertmanager + Prometheus rule mount ship as a compose overlay at `monitoring/prometheus/docker-compose.prometheus.yml` (matching the 26-02 GlitchTip overlay precedent -- main `docker-compose.yml` locked by `pre_guard_release_files.py` PreToolUse hook). Grafana dashboard `fintrack-slo.json` auto-loaded by the existing dashboards provisioner. Backend exposes four `fintrack_price_sync_last_success_timestamp_seconds{source=...}` Micrometer gauges with a NaN-before-record contract that prevents cold-boot false-positive alerts. See `docs/OPERATIONS.md` -> "SLI/SLO dashboard and burn-rate alerts" for the canonical operator runbook.
- Phase 26-02: self-hosted GlitchTip stack shipped as a separate compose overlay at `monitoring/glitchtip/docker-compose.glitchtip.yml` (web + Celery worker, reusing existing Postgres + Redis via `external: true` network), Sentry SDK wired into Spring Boot via `sentry-spring-boot-starter-jakarta`, release-tagged with `IDENTITY.yaml` version, PII scrubbed via `AuditPiiRedactor` in a `BeforeSendCallback`, trace IDs cross-linked to Tempo (26-01). Operator-facing env vars + the `docker compose -f docker-compose.yml -f monitoring/glitchtip/docker-compose.glitchtip.yml up -d` invocation documented in `docs/OPERATIONS.md` under "GlitchTip / Sentry release tagging" (.env.example and docker-compose.yml not updated -- Claude tooling is denied write access to both via the project deny rule + pre_guard_release_files.py hook).

### Changed
### Deprecated
### Removed
### Fixed
### Security

## [1.1.0] - 2026-05-07

### Added
- Phase 25-01: cross-cutting events via `ApplicationEventPublisher` with three `@TransactionalEventListener(AFTER_COMMIT)` listeners (holding projection, bill-paid notifications, budget-rule evaluation).
- Phase 25-02: Caffeine + Spring Cache on hot reads (assets, user settings, category lookup) with explicit `@CacheEvict` / `@CachePut` invalidation on writes.
- Phase 25-03: virtual-thread executor for price refreshes; `spring.threads.virtual.enabled` and a `priceVirtualExecutor` bean fan out provider calls in parallel.

### Changed
- Phase 25-03: `PriceSyncService` refactored into a fetch-fan-out + persist-once shape; `Thread.sleep` replaced by a `Semaphore`-gated parallelism cap; `@Transactional` shrunk to the write window only.

## [1.0.0] - 2026-04-18

### Added
- Initial public release covering portfolio, budget, bill, audit, and security foundations (Phases 1-22).

[Unreleased]: https://github.com/GTRows/fintrack/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/GTRows/fintrack/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/GTRows/fintrack/releases/tag/v1.0.0
