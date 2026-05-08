package com.fintrack.imports.bank;

import com.fintrack.budget.rule.TransactionCategoryRuleRepository;
import com.fintrack.common.entity.TransactionCategoryRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Best-effort categorisation for imported CSV rows. Loads the user's {@link
 * TransactionCategoryRule} entries once at the start of an import and applies them in priority
 * order; first-match wins. Malformed regex patterns log at WARN and skip; the row falls through
 * with {@code null} categoryId. Patterns are compiled with {@link Pattern#CASE_INSENSITIVE} and
 * matched via {@link java.util.regex.Matcher#find()} against the lower-cased description.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankCsvCategoryMatcher {

    private final TransactionCategoryRuleRepository ruleRepo;

    public Resolver resolverFor(UUID userId) {
        List<TransactionCategoryRule> rules =
                ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId);
        return new Resolver(rules);
    }

    public static final class Resolver {
        private final List<CompiledRule> compiled;

        Resolver(List<TransactionCategoryRule> rules) {
            this.compiled = new ArrayList<>(rules.size());
            for (TransactionCategoryRule r : rules) {
                String pattern = r.getPattern();
                if (pattern == null || pattern.isBlank()) {
                    log.warn("Skipping rule with blank pattern: ruleId={}", r.getId());
                    continue;
                }
                try {
                    Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                    compiled.add(new CompiledRule(p, r.getCategoryId()));
                } catch (PatternSyntaxException e) {
                    log.warn(
                            "Skipping malformed category rule pattern: ruleId={} err={}",
                            r.getId(),
                            e.getMessage());
                }
            }
        }

        public UUID resolve(String description) {
            if (description == null || description.isBlank()) {
                return null;
            }
            String lower = description.toLowerCase(Locale.ROOT);
            for (CompiledRule cr : compiled) {
                if (cr.pattern().matcher(lower).find()) {
                    return cr.categoryId();
                }
            }
            return null;
        }
    }

    private record CompiledRule(Pattern pattern, UUID categoryId) {}
}
