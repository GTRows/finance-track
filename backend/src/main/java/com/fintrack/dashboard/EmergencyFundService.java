package com.fintrack.dashboard;

import com.fintrack.account.AccountRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.dashboard.dto.EmergencyFundResponse;
import com.fintrack.dashboard.dto.EmergencyFundResponse.CurrencyBucket;
import com.fintrack.settings.UserSettingsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the dashboard tile rollup for emergency-fund coverage. Sums {@code
 * Account.currentBalance} across the operator's chosen account types and divides by the trailing
 * 12-month average expense to surface "months covered" with a red/amber/green band.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyFundService {

    static final int SAMPLE_MONTHS = 12;
    static final int MIN_SAMPLES = 3;
    static final BigDecimal RED_BAND = new BigDecimal("3");
    static final BigDecimal AMBER_BAND = new BigDecimal("6");
    static final BigDecimal SENTINEL_INFINITE = new BigDecimal("999");
    static final List<Account.AccountType> DEFAULT_INCLUDED =
            List.of(Account.AccountType.BANK_SAVINGS);

    private final AccountRepository accountRepository;
    private final TransactionRepository txnRepo;
    private final UserSettingsRepository userSettingsRepo;

    @Transactional(readOnly = true)
    public EmergencyFundResponse compute(UUID userId) {
        List<Account.AccountType> includedTypes = resolveIncludedTypes(userId);
        List<CurrencyBucket> buckets =
                accountRepository.sumBalancesByTypeForUser(userId, includedTypes).stream()
                        .map(row -> new CurrencyBucket((String) row[0], (BigDecimal) row[1]))
                        .toList();

        BigDecimal currentReserve =
                buckets.stream()
                        .map(CurrencyBucket::totalBalance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Trailing-12-month walker over EXPENSE rows; mirrors the FireService averages helper to
        // avoid pulling its private internals into 27-03's blast radius.
        YearMonth current = YearMonth.now();
        BigDecimal expenseSum = BigDecimal.ZERO;
        int samples = 0;
        for (int i = 0; i < SAMPLE_MONTHS; i++) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            BigDecimal exp =
                    nvl(
                            txnRepo.sumByUserIdAndTypeAndDateRange(
                                    userId, BudgetTransaction.TxnType.EXPENSE, from, to));
            if (exp.signum() == 0) {
                continue;
            }
            expenseSum = expenseSum.add(exp);
            samples++;
        }
        BigDecimal monthlyAverageExpense =
                samples > 0
                        ? expenseSum.divide(BigDecimal.valueOf(samples), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        BigDecimal monthsCovered;
        String status;
        if (samples < MIN_SAMPLES) {
            monthsCovered = null;
            status = "insufficient-data";
        } else if (monthlyAverageExpense.signum() == 0) {
            monthsCovered = SENTINEL_INFINITE;
            status = "green";
        } else {
            monthsCovered = currentReserve.divide(monthlyAverageExpense, 1, RoundingMode.HALF_UP);
            if (monthsCovered.compareTo(RED_BAND) < 0) {
                status = "red";
            } else if (monthsCovered.compareTo(AMBER_BAND) <= 0) {
                status = "amber";
            } else {
                status = "green";
            }
        }

        return new EmergencyFundResponse(
                currentReserve,
                buckets,
                monthlyAverageExpense,
                monthsCovered,
                status,
                includedTypes,
                samples);
    }

    private List<Account.AccountType> resolveIncludedTypes(UUID userId) {
        UserSettings settings = userSettingsRepo.findById(userId).orElse(null);
        if (settings == null
                || settings.getEmergencyFundIncludeTypes() == null
                || settings.getEmergencyFundIncludeTypes().isEmpty()) {
            return DEFAULT_INCLUDED;
        }
        List<Account.AccountType> out = new ArrayList<>();
        for (String name : settings.getEmergencyFundIncludeTypes()) {
            try {
                out.add(Account.AccountType.valueOf(name));
            } catch (IllegalArgumentException ex) {
                log.warn(
                        "Unknown emergency-fund account type ignored: userId={} value={}",
                        userId,
                        name);
            }
        }
        // BANK_SAVINGS is always-on -- defensively re-add if a corrupted settings row dropped it.
        if (!out.contains(Account.AccountType.BANK_SAVINGS)) {
            out.add(0, Account.AccountType.BANK_SAVINGS);
        }
        return out;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
