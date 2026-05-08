package com.fintrack.account;

import com.fintrack.common.entity.Account;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a signed delta to {@link Account#getCurrentBalance()} in its own short transaction
 * (REQUIRES_NEW). Called from {@link AccountBalanceListener} after the writer's transaction has
 * already committed. A missing account row (operator hard-deleted between the writer's commit and
 * the listener's read) is logged at WARN and silently skipped -- the FK is ON DELETE SET NULL so
 * the transaction row already lost its account_id link.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountBalanceUpdater {

    private final AccountRepository accountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(UUID accountId, BigDecimal delta) {
        if (accountId == null || delta == null || delta.signum() == 0) {
            return;
        }
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            log.warn(
                    "Account balance rollup skipped: account row not found accountId={}",
                    accountId);
            return;
        }
        BigDecimal current =
                account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO;
        account.setCurrentBalance(current.add(delta));
        accountRepository.save(account);
        log.debug(
                "Account balance updated: accountId={} delta={} newBalance={}",
                accountId,
                delta,
                account.getCurrentBalance());
    }
}
