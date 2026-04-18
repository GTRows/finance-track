package com.fintrack.budget.rule;

import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.rule.dto.CategoryRuleResponse;
import com.fintrack.budget.rule.dto.UpsertCategoryRuleRequest;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.TransactionCategoryRule;
import com.fintrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based auto-categorization. When a transaction is created without an explicit
 * category, scan its description against active rules for that txn type (lowercase
 * substring match), ordered by priority then age. First match wins; match_count
 * increments so busy rules can be surfaced in the UI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionCategoryRuleService {

    private final TransactionCategoryRuleRepository ruleRepo;
    private final IncomeCategoryRepository incomeRepo;
    private final ExpenseCategoryRepository expenseRepo;

    @Transactional(readOnly = true)
    public List<CategoryRuleResponse> list(UUID userId) {
        List<TransactionCategoryRule> rules = ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId);
        if (rules.isEmpty()) return List.of();
        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        return rules.stream()
                .map(r -> {
                    String[] info = catLookup.getOrDefault(r.getCategoryId(), new String[]{"Unknown", null});
                    return CategoryRuleResponse.from(r, info[0], info[1]);
                })
                .toList();
    }

    @Transactional
    public CategoryRuleResponse create(UUID userId, UpsertCategoryRuleRequest req) {
        validateCategoryOwnership(userId, req.categoryId(), req.txnType());

        TransactionCategoryRule rule = TransactionCategoryRule.builder()
                .userId(userId)
                .pattern(req.pattern().trim())
                .categoryId(req.categoryId())
                .txnType(req.txnType())
                .priority(req.priority() != null ? req.priority() : 100)
                .build();
        rule = ruleRepo.save(rule);
        log.info("Category rule created: id={} pattern='{}' type={}", rule.getId(), rule.getPattern(), rule.getTxnType());

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        String[] info = catLookup.getOrDefault(rule.getCategoryId(), new String[]{"Unknown", null});
        return CategoryRuleResponse.from(rule, info[0], info[1]);
    }

    @Transactional
    public CategoryRuleResponse update(UUID userId, UUID ruleId, UpsertCategoryRuleRequest req) {
        TransactionCategoryRule rule = ruleRepo.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found"));
        validateCategoryOwnership(userId, req.categoryId(), req.txnType());

        rule.setPattern(req.pattern().trim());
        rule.setCategoryId(req.categoryId());
        rule.setTxnType(req.txnType());
        if (req.priority() != null) rule.setPriority(req.priority());
        rule = ruleRepo.save(rule);

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        String[] info = catLookup.getOrDefault(rule.getCategoryId(), new String[]{"Unknown", null});
        return CategoryRuleResponse.from(rule, info[0], info[1]);
    }

    @Transactional
    public void delete(UUID userId, UUID ruleId) {
        TransactionCategoryRule rule = ruleRepo.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found"));
        ruleRepo.delete(rule);
        log.info("Category rule deleted: id={}", ruleId);
    }

    /**
     * Find the first rule whose pattern (case-insensitive) appears in the description.
     * Returns the matched category id, or empty if no rule matched. On a hit, the rule's
     * match_count is incremented.
     */
    @Transactional
    public Optional<UUID> matchFor(UUID userId, BudgetTransaction.TxnType txnType, String description) {
        if (description == null || description.isBlank()) return Optional.empty();
        String haystack = description.toLowerCase(Locale.ROOT);

        List<TransactionCategoryRule> rules = ruleRepo
                .findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(userId, txnType);
        for (TransactionCategoryRule rule : rules) {
            String needle = rule.getPattern().toLowerCase(Locale.ROOT);
            if (!needle.isBlank() && haystack.contains(needle)) {
                rule.setMatchCount(rule.getMatchCount() + 1);
                ruleRepo.save(rule);
                log.debug("Rule matched: id={} pattern='{}' -> category={}",
                        rule.getId(), rule.getPattern(), rule.getCategoryId());
                return Optional.of(rule.getCategoryId());
            }
        }
        return Optional.empty();
    }

    private void validateCategoryOwnership(UUID userId, UUID categoryId, BudgetTransaction.TxnType txnType) {
        if (txnType == BudgetTransaction.TxnType.INCOME) {
            incomeRepo.findByIdAndUserId(categoryId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Income category not found"));
        } else {
            expenseRepo.findByIdAndUserId(categoryId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Expense category not found"));
        }
    }

    private Map<UUID, String[]> buildCategoryLookup(UUID userId) {
        Map<UUID, String[]> map = new HashMap<>();
        incomeRepo.findByUserIdOrderByNameAsc(userId)
                .forEach(c -> map.put(c.getId(), new String[]{c.getName(), c.getColor()}));
        expenseRepo.findByUserIdOrderByNameAsc(userId)
                .forEach(c -> map.put(c.getId(), new String[]{c.getName(), c.getColor()}));
        return map;
    }
}
