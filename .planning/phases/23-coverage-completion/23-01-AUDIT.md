# Phase 23 Plan 01 - Repository @DataJpaTest coverage audit

37 Spring Data repositories declared under `backend/src/main/java/com/fintrack/`. Cross-referenced against `*RepositoryDataJpaTest.java` files under `backend/src/test/java/com/fintrack/`.

| Repository | Source path | Has @DataJpaTest | Notable query methods worth testing |
|---|---|---|---|
| AdminSettingRepository | admin/AdminSettingRepository.java | No | None - inherits JpaRepository defaults only. SKIP. |
| AlertNotificationRepository | alert/AlertNotificationRepository.java | No | findByUserIdOrderByCreatedAtDesc, countByUserIdAndReadAtIsNull, findByIdAndUserId |
| PriceAlertRepository | alert/PriceAlertRepository.java | No | findAllByUserId (JOIN FETCH), findAllActiveWithAsset, findByIdAndUserId |
| AssetRepository | asset/AssetRepository.java | No | findAllByOrderBySymbolAsc, findByAssetTypeOrderBySymbolAsc, findBySymbolAndAssetType |
| AuditLogRepository | audit/AuditLogRepository.java | No | findAllByOrderByCreatedAtDesc(Pageable), findByUserIdOrderByCreatedAtDesc, findByActionOrderByCreatedAtDesc |
| EmailVerificationRepository | auth/EmailVerificationRepository.java | No | findByToken, consumeOutstandingForUser (@Modifying), deleteExpired |
| PasswordResetRepository | auth/PasswordResetRepository.java | No | findByToken, consumeOutstandingForUser, deleteExpired |
| RefreshTokenRepository | auth/RefreshTokenRepository.java | No | findByToken, findByUserIdAndExpiresAtAfter..., findByIdAndUserId, deleteByUserId, deleteByUserIdExcept, deleteByToken, deleteExpired |
| TotpRecoveryCodeRepository | auth/TotpRecoveryCodeRepository.java | No | findActiveByUserId, countByUserIdAndConsumedAtIsNull, deleteByUserId |
| UserRepository | auth/UserRepository.java | YES | (already covered) |
| BillPaymentRepository | bills/BillPaymentRepository.java | No | findByBillIdOrderByPeriodDesc, findTop2ByBillIdAndStatusOrderByPeriodDesc, findByBillIdAndPeriod |
| BillRepository | bills/BillRepository.java | YES | (already covered) |
| BudgetRuleRepository | budget/BudgetRuleRepository.java | No | findByUserIdOrderByCreatedAtDesc, findByIdAndUserId, findByUserIdAndCategoryId |
| ExpenseCategoryRepository | budget/ExpenseCategoryRepository.java | YES | (already covered) |
| IncomeCategoryRepository | budget/IncomeCategoryRepository.java | YES | (already covered) |
| MonthlySummaryRepository | budget/MonthlySummaryRepository.java | No | findByUserIdOrderByPeriodDesc, findByUserIdAndPeriod |
| TransactionRepository | budget/TransactionRepository.java | YES | (already covered) |
| AllocationBucketRepository | budget/allocation/AllocationBucketRepository.java | No | findByUserIdOrderByOrdinalAsc, deleteByUserId |
| RecurringTemplateRepository | budget/recurring/RecurringTemplateRepository.java | No | findByUserIdOrderByCreatedAtAsc, findByIdAndUserId, findByActiveTrue |
| TransactionCategoryRuleRepository | budget/rule/TransactionCategoryRuleRepository.java | No | findByUserIdAndTxnTypeOrderByPriorityAsc..., findByUserIdOrderByPriorityAsc..., findByIdAndUserId |
| DebtPaymentRepository | debt/DebtPaymentRepository.java | No | findByDebtIdOrderByPaymentDateAsc, sumByDebtId |
| DebtRepository | debt/DebtRepository.java | YES | (already covered) |
| NetWorthEventRepository | networth/NetWorthEventRepository.java | YES | (already covered) |
| PortfolioRepository | portfolio/PortfolioRepository.java | YES | (already covered) |
| AllocationTargetRepository | portfolio/allocation/AllocationTargetRepository.java | No | findByPortfolioId, deleteByPortfolioId |
| DividendRepository | portfolio/dividend/DividendRepository.java | No | findByPortfolioIdOrderByPaymentDateDesc, findByAssetIdOrderByPaymentDateDesc, findByIdAndPortfolioId, sumNetByPortfolioAndAsset, sumNetByPortfoliosAndRange |
| HoldingRepository | portfolio/holding/HoldingRepository.java | No | findByPortfolioId (@Query), findByPortfolioIdAndAssetId, findByIdAndPortfolioId |
| SnapshotRepository | portfolio/snapshot/SnapshotRepository.java | No | findByPortfolioIdOrderBySnapshotDateAsc, findByPortfolioIdAndSnapshotDate, sumLatestTotalValueTry |
| InvestmentTransactionRepository | portfolio/transaction/InvestmentTransactionRepository.java | No | findByPortfolioIdOrderByTxnDateDesc..., findByIdAndPortfolioId, findByPortfolioIdInAndNotesStartingWith |
| PriceHistoryRepository | price/PriceHistoryRepository.java | No | findSeries (@Query) |
| PushSubscriptionRepository | push/PushSubscriptionRepository.java | No | findByUserId, findByEndpoint, deleteByEndpoint |
| SavingsContributionRepository | savings/SavingsContributionRepository.java | No | findByGoalIdOrderByContributionDateDesc, sumByGoalId, deleteByGoalId |
| SavingsGoalRepository | savings/SavingsGoalRepository.java | YES | (already covered) |
| UserSettingsRepository | settings/UserSettingsRepository.java | No | None - inherits JpaRepository defaults only. SKIP. |
| TagRepository | tag/TagRepository.java | YES | (already covered) |
| TransactionTagRepository | tag/TransactionTagRepository.java | No | findByTransactionId, findByTransactionIds (@Query), deleteByTransactionId, deleteByTransactionIdAndTagId, deleteByTagId, countByTagId (native) |
| WatchlistRepository | watchlist/WatchlistRepository.java | No | findByUserIdOrderByCreatedAtDesc, findByUserIdAndAssetId, existsByUserIdAndAssetId, deleteByUserIdAndAssetId |

## Summary

- 37 repositories total
- 10 already covered (UserRepository, BillRepository, TransactionRepository, ExpenseCategoryRepository, IncomeCategoryRepository, DebtRepository, NetWorthEventRepository, PortfolioRepository, SavingsGoalRepository, TagRepository)
- 27 missing
- 2 of those 27 will be intentionally skipped (no custom queries beyond JpaRepository defaults): AdminSettingRepository, UserSettingsRepository
- 25 new `*RepositoryDataJpaTest` suites required
