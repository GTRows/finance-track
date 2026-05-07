package com.fintrack.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class AccountRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AccountRepository repo;
    @Autowired UserRepository userRepo;

    private UUID seedUser(String username) {
        return userRepo.save(
                        User.builder()
                                .username(username)
                                .email(username + "@example.com")
                                .password("bcrypt-hash")
                                .role(User.Role.USER)
                                .build())
                .getId();
    }

    private Account account(
            UUID userId,
            String name,
            Account.AccountType type,
            String currency,
            String balance,
            boolean archived) {
        return Account.builder()
                .userId(userId)
                .name(name)
                .accountType(type)
                .currency(currency)
                .currentBalance(new BigDecimal(balance))
                .archived(archived)
                .build();
    }

    @Test
    void findByUserIdAndArchivedFalseExcludesArchived() {
        UUID userId = seedUser("alice");
        repo.save(account(userId, "Main", Account.AccountType.BANK_CHECKING, "TRY", "100", false));
        repo.save(account(userId, "Old", Account.AccountType.BANK_SAVINGS, "TRY", "50", true));

        assertThat(repo.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId))
                .extracting(Account::getName)
                .containsExactly("Main");
    }

    @Test
    void existsByUserIdAndNameIgnoreCaseAndArchivedFalseIsCaseInsensitive() {
        UUID userId = seedUser("ada");
        UUID otherUserId = seedUser("bob");
        repo.save(account(userId, "Main", Account.AccountType.BANK_CHECKING, "TRY", "0", false));

        assertThat(repo.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, "Main")).isTrue();
        assertThat(repo.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, "main")).isTrue();
        assertThat(repo.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, "MAIN")).isTrue();
        assertThat(repo.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(otherUserId, "Main"))
                .isFalse();

        // Archive the row and assert it no longer blocks.
        Account live =
                repo.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId).stream()
                        .findFirst()
                        .orElseThrow();
        live.setArchived(true);
        repo.save(live);

        assertThat(repo.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, "Main")).isFalse();
    }

    @Test
    void sumBalancesByCurrencyForUserGroupsAndSumsLiveOnly() {
        UUID userId = seedUser("ozge");
        repo.save(account(userId, "TRY-1", Account.AccountType.BANK_CHECKING, "TRY", "100", false));
        repo.save(account(userId, "TRY-2", Account.AccountType.BANK_SAVINGS, "TRY", "200", false));
        repo.save(account(userId, "USD-1", Account.AccountType.BROKERAGE_CASH, "USD", "50", false));
        repo.save(account(userId, "USD-old", Account.AccountType.CASH, "USD", "999", true));

        List<Object[]> rows = repo.sumBalancesByCurrencyForUser(userId);

        assertThat(rows).hasSize(2);
        Map<String, BigDecimal> byCurrency =
                rows.stream().collect(Collectors.toMap(r -> (String) r[0], r -> (BigDecimal) r[1]));
        assertThat(byCurrency.get("TRY")).isEqualByComparingTo("300");
        assertThat(byCurrency.get("USD")).isEqualByComparingTo("50");
        assertThat(rows.get(0)[0]).isEqualTo("TRY");
        assertThat(rows.get(1)[0]).isEqualTo("USD");
    }

    @Test
    void countByUserIdAndArchivedTrueCountsArchivedOnly() {
        UUID userId = seedUser("kemal");
        repo.save(account(userId, "Live-1", Account.AccountType.BANK_CHECKING, "TRY", "0", false));
        repo.save(account(userId, "Live-2", Account.AccountType.BANK_SAVINGS, "TRY", "0", false));
        repo.save(account(userId, "Old-1", Account.AccountType.CASH, "TRY", "0", true));
        repo.save(account(userId, "Old-2", Account.AccountType.OTHER, "TRY", "0", true));
        repo.save(account(userId, "Old-3", Account.AccountType.OTHER, "TRY", "0", true));

        assertThat(repo.countByUserIdAndArchivedTrue(userId)).isEqualTo(3);
        assertThat(repo.countByUserIdAndArchivedFalse(userId)).isEqualTo(2);
    }
}
