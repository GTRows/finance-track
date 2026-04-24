package com.fintrack.budget.recurring;

import com.fintrack.common.entity.RecurringTemplate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTemplateScheduler {

    private final RecurringTemplateRepository templateRepo;
    private final RecurringTemplateService service;

    /** Runs every day at 06:00 local time. Materializes any active templates due today. */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void materializeDueTemplates() {
        LocalDate today = LocalDate.now();
        List<RecurringTemplate> templates = templateRepo.findByActiveTrue();
        int fired = 0;
        for (RecurringTemplate t : templates) {
            LocalDate scheduled =
                    RecurringTemplateService.scheduledDateFor(
                            t.getDayOfMonth(), YearMonth.from(today));
            if (scheduled.isAfter(today)) continue;
            LocalDate last = t.getLastMaterializedOn();
            if (last != null && !last.isBefore(scheduled)) continue;
            service.materialize(t, scheduled);
            fired++;
        }
        if (fired > 0) {
            log.info("Recurring templates materialized: count={} date={}", fired, today);
        }
    }
}
