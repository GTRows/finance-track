package com.fintrack.account;

import com.fintrack.account.dto.AccountResponse;
import com.fintrack.account.dto.AccountTotalsResponse;
import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.dto.UpdateAccountRequest;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Account;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account business logic. All operations are scoped to the authenticated user. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final int MAX_ACCOUNTS_PER_USER = 50;

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    /** Lists all live accounts for the given user. */
    @Transactional(readOnly = true)
    public List<AccountResponse> listForUser(UUID userId) {
        return accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    /**
     * Returns a single live account belonging to the given user.
     *
     * @throws ResourceNotFoundException if the account does not exist, is archived, or is not owned
     *     by the user
     */
    @Transactional(readOnly = true)
    public AccountResponse getForUser(UUID userId, UUID accountId) {
        Account account =
                accountRepository
                        .findByIdAndUserIdAndArchivedFalse(accountId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return AccountResponse.from(account);
    }

    /**
     * Creates a new account for the given user.
     *
     * @throws BusinessRuleException if the user has reached the account cap or a live account with
     *     the same case-insensitive name already exists
     */
    @Transactional
    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        long currentCount = accountRepository.countByUserIdAndArchivedFalse(userId);
        if (currentCount >= MAX_ACCOUNTS_PER_USER) {
            String username = currentUsername();
            BusinessRuleException ex =
                    new BusinessRuleException(
                            "Account limit reached (max " + MAX_ACCOUNTS_PER_USER + ")",
                            "ACCOUNT_LIMIT");
            auditService.failure(AuditAction.ACCOUNT_CREATED, userId, username, ex.getMessage());
            throw ex;
        }

        String name = request.name().trim();
        if (accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, name)) {
            String username = currentUsername();
            BusinessRuleException ex =
                    new BusinessRuleException(
                            "Account name already exists", "ACCOUNT_NAME_DUPLICATE");
            auditService.failure(AuditAction.ACCOUNT_CREATED, userId, username, ex.getMessage());
            throw ex;
        }

        BigDecimal opening =
                request.openingBalance() != null ? request.openingBalance() : BigDecimal.ZERO;
        Account account =
                Account.builder()
                        .userId(userId)
                        .name(name)
                        .accountType(request.type())
                        .currency(request.currency().trim().toUpperCase(Locale.ROOT))
                        .institution(
                                request.institution() != null ? request.institution().trim() : null)
                        .accountNumberSuffix(
                                request.accountNumberSuffix() != null
                                        ? request.accountNumberSuffix().trim()
                                        : null)
                        .notes(request.notes() != null ? request.notes().trim() : null)
                        .currentBalance(opening)
                        .archived(false)
                        .build();

        account = accountRepository.save(account);
        log.info(
                "Account created: id={} name={} userId={} currency={}",
                account.getId(),
                account.getName(),
                userId,
                account.getCurrency());
        auditService.success(
                AuditAction.ACCOUNT_CREATED,
                userId,
                currentUsername(),
                "id=" + account.getId() + " currency=" + account.getCurrency());
        return AccountResponse.from(account);
    }

    /** Updates a live account. Type is immutable; currency and balance are mutable. */
    @Transactional
    public AccountResponse update(UUID userId, UUID accountId, UpdateAccountRequest request) {
        Account account =
                accountRepository
                        .findByIdAndUserIdAndArchivedFalse(accountId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        String newName = request.name().trim();
        if (!newName.equalsIgnoreCase(account.getName())
                && accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(
                        userId, newName)) {
            String username = currentUsername();
            BusinessRuleException ex =
                    new BusinessRuleException(
                            "Account name already exists", "ACCOUNT_NAME_DUPLICATE");
            auditService.failure(AuditAction.ACCOUNT_UPDATED, userId, username, ex.getMessage());
            throw ex;
        }

        account.setName(newName);
        account.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        account.setInstitution(
                request.institution() != null ? request.institution().trim() : null);
        account.setAccountNumberSuffix(
                request.accountNumberSuffix() != null
                        ? request.accountNumberSuffix().trim()
                        : null);
        account.setNotes(request.notes() != null ? request.notes().trim() : null);
        account.setCurrentBalance(request.currentBalance());

        log.info("Account updated: id={} userId={}", accountId, userId);
        auditService.success(
                AuditAction.ACCOUNT_UPDATED, userId, currentUsername(), "id=" + accountId);
        return AccountResponse.from(account);
    }

    /** Soft-archives an account (sets archived = true). */
    @Transactional
    public void delete(UUID userId, UUID accountId) {
        Account account =
                accountRepository
                        .findByIdAndUserIdAndArchivedFalse(accountId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        account.setArchived(true);
        log.info("Account archived: id={} userId={}", accountId, userId);
        auditService.success(
                AuditAction.ACCOUNT_DELETED, userId, currentUsername(), "id=" + accountId);
    }

    /** Returns aggregate counts and per-currency balance rollup for the totals strip. */
    @Transactional(readOnly = true)
    public AccountTotalsResponse totalsForUser(UUID userId) {
        int liveCount = (int) accountRepository.countByUserIdAndArchivedFalse(userId);
        int archivedCount = (int) accountRepository.countByUserIdAndArchivedTrue(userId);
        List<AccountTotalsResponse.CurrencyTotal> byCurrency =
                accountRepository.sumBalancesByCurrencyForUser(userId).stream()
                        .map(
                                row ->
                                        new AccountTotalsResponse.CurrencyTotal(
                                                (String) row[0], (BigDecimal) row[1]))
                        .toList();
        return new AccountTotalsResponse(liveCount, archivedCount, byCurrency);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
