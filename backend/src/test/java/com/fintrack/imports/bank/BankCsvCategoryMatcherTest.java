package com.fintrack.imports.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fintrack.budget.rule.TransactionCategoryRuleRepository;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.TransactionCategoryRule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankCsvCategoryMatcherTest {

    @Mock private TransactionCategoryRuleRepository ruleRepo;

    @InjectMocks private BankCsvCategoryMatcher matcher;

    private TransactionCategoryRule rule(String pattern, UUID categoryId, int priority) {
        return TransactionCategoryRule.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .pattern(pattern)
                .categoryId(categoryId)
                .txnType(BudgetTransaction.TxnType.EXPENSE)
                .priority(priority)
                .matchCount(0)
                .build();
    }

    @Test
    void resolve_matchesFirstRule_returnsCategory() {
        UUID userId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId))
                .thenReturn(List.of(rule("netflix", catId, 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve("NETFLIX ABONELIK")).isEqualTo(catId);
    }

    @Test
    void resolve_matchesNoRule_returnsNull() {
        UUID userId = UUID.randomUUID();
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId))
                .thenReturn(List.of(rule("netflix", UUID.randomUUID(), 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve("MARKET ALISVERIS")).isNull();
    }

    @Test
    void resolve_caseInsensitive_matchesUppercaseDescription() {
        UUID userId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId))
                .thenReturn(List.of(rule("Spotify", catId, 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve("SPOTIFY PREMIUM AYLIK")).isEqualTo(catId);
    }

    @Test
    void resolve_malformedPattern_skippedWithoutThrow() {
        UUID userId = UUID.randomUUID();
        UUID catGood = UUID.randomUUID();
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId))
                .thenReturn(List.of(rule("[", UUID.randomUUID(), 50), rule("rent", catGood, 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve("RENT PAYMENT")).isEqualTo(catGood);
    }

    @Test
    void resolve_priorityOrder_lowestPriorityWins() {
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        // Repository returns rules in priority order; priority 50 comes before 100.
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId))
                .thenReturn(List.of(rule("rent", first, 50), rule("rent", second, 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve("RENT PAYMENT")).isEqualTo(first);
    }

    @Test
    void resolve_nullDescription_returnsNull() {
        UUID userId = UUID.randomUUID();
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(any()))
                .thenReturn(List.of(rule("foo", UUID.randomUUID(), 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve(null)).isNull();
    }

    @Test
    void resolve_blankDescription_returnsNull() {
        UUID userId = UUID.randomUUID();
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(any()))
                .thenReturn(List.of(rule("foo", UUID.randomUUID(), 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve("   ")).isNull();
    }

    @Test
    void resolve_blankPattern_skipped() {
        UUID userId = UUID.randomUUID();
        UUID catGood = UUID.randomUUID();
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId))
                .thenReturn(List.of(rule("", UUID.randomUUID(), 50), rule("rent", catGood, 100)));
        BankCsvCategoryMatcher.Resolver r = matcher.resolverFor(userId);
        assertThat(r.resolve("RENT PAYMENT")).isEqualTo(catGood);
    }
}
