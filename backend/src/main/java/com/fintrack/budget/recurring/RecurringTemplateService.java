package com.fintrack.budget.recurring;

import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.recurring.dto.RecurringTemplateResponse;
import com.fintrack.budget.recurring.dto.UpsertRecurringRequest;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.RecurringTemplate;
import com.fintrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTemplateService {

    private final RecurringTemplateRepository templateRepo;
    private final TransactionRepository txnRepo;
    private final IncomeCategoryRepository incomeRepo;
    private final ExpenseCategoryRepository expenseRepo;

    @Transactional(readOnly = true)
    public List<RecurringTemplateResponse> listForUser(UUID userId) {
        List<RecurringTemplate> templates = templateRepo.findByUserIdOrderByCreatedAtAsc(userId);
        if (templates.isEmpty()) return List.of();
        Map<UUID, String> lookup = categoryLookup(userId);
        LocalDate today = LocalDate.now();
        return templates.stream()
                .map(t -> RecurringTemplateResponse.from(
                        t,
                        t.getCategoryId() != null ? lookup.get(t.getCategoryId()) : null,
                        nextDueOn(t, today)))
                .toList();
    }

    @Transactional
    public RecurringTemplateResponse create(UUID userId, UpsertRecurringRequest req) {
        RecurringTemplate t = RecurringTemplate.builder()
                .userId(userId)
                .txnType(req.txnType())
                .amount(req.amount())
                .categoryId(req.categoryId())
                .description(req.description())
                .dayOfMonth(req.dayOfMonth())
                .active(req.active() == null || req.active())
                .build();
        t = templateRepo.save(t);
        log.info("Recurring template created: id={} type={} dom={}", t.getId(), t.getTxnType(), t.getDayOfMonth());
        return toResponse(userId, t);
    }

    @Transactional
    public RecurringTemplateResponse update(UUID userId, UUID id, UpsertRecurringRequest req) {
        RecurringTemplate t = templateRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
        t.setTxnType(req.txnType());
        t.setAmount(req.amount());
        t.setCategoryId(req.categoryId());
        t.setDescription(req.description());
        t.setDayOfMonth(req.dayOfMonth());
        if (req.active() != null) t.setActive(req.active());
        return toResponse(userId, t);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        RecurringTemplate t = templateRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
        templateRepo.delete(t);
        log.info("Recurring template deleted: id={}", id);
    }

    @Transactional
    public RecurringTemplateResponse runNow(UUID userId, UUID id) {
        RecurringTemplate t = templateRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
        materialize(t, LocalDate.now());
        return toResponse(userId, t);
    }

    /** Creates a transaction from a template on the given date and updates the template. */
    @Transactional
    public void materialize(RecurringTemplate t, LocalDate on) {
        BudgetTransaction txn = BudgetTransaction.builder()
                .userId(t.getUserId())
                .txnType(t.getTxnType())
                .amount(t.getAmount())
                .categoryId(t.getCategoryId())
                .description(t.getDescription())
                .txnDate(on)
                .recurring(true)
                .build();
        txnRepo.save(txn);
        t.setLastMaterializedOn(on);
        log.info("Recurring template materialized: templateId={} txnDate={} amount={}",
                t.getId(), on, t.getAmount());
    }

    /** Computes the effective day for a given month (clamps to month length). */
    public static LocalDate scheduledDateFor(int dayOfMonth, YearMonth ym) {
        int day = Math.min(dayOfMonth, ym.lengthOfMonth());
        return ym.atDay(day);
    }

    /** Returns the next date this template will fire on (today or later). */
    public static LocalDate nextDueOn(RecurringTemplate t, LocalDate today) {
        LocalDate thisMonth = scheduledDateFor(t.getDayOfMonth(), YearMonth.from(today));
        LocalDate last = t.getLastMaterializedOn();
        if (!thisMonth.isBefore(today) && (last == null || last.isBefore(thisMonth))) {
            return thisMonth;
        }
        return scheduledDateFor(t.getDayOfMonth(), YearMonth.from(today).plusMonths(1));
    }

    private RecurringTemplateResponse toResponse(UUID userId, RecurringTemplate t) {
        String categoryName = null;
        if (t.getCategoryId() != null) {
            categoryName = categoryLookup(userId).get(t.getCategoryId());
        }
        return RecurringTemplateResponse.from(t, categoryName, nextDueOn(t, LocalDate.now()));
    }

    private Map<UUID, String> categoryLookup(UUID userId) {
        Map<UUID, String> map = new HashMap<>();
        incomeRepo.findByUserIdOrderByNameAsc(userId).forEach(c -> map.put(c.getId(), c.getName()));
        expenseRepo.findByUserIdOrderByNameAsc(userId).forEach(c -> map.put(c.getId(), c.getName()));
        return map;
    }
}
