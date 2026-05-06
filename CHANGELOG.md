# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
