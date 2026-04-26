package com.fintrack.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean ReportService reportService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void portfolioReportReturnsPdf() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46};
        when(reportService.generatePortfolioPdf(eq(userId), eq(pid))).thenReturn(pdf);

        mockMvc.perform(get("/api/v1/reports/portfolio/" + pid))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "attachment; filename=portfolio-report.pdf"))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void budgetCsvReturnsCsv() throws Exception {
        stubAuthenticatedUser();
        byte[] csv = "date,amount\n2026-04-01,100\n".getBytes();
        when(reportService.generateBudgetCsv(
                        eq(userId), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(csv);

        mockMvc.perform(
                        get("/api/v1/reports/budget")
                                .param("from", "2026-04-01")
                                .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(content().bytes(csv));
    }

    @Test
    void budgetXlsxReturnsXlsx() throws Exception {
        stubAuthenticatedUser();
        byte[] xlsx = new byte[] {0x50, 0x4B, 0x03, 0x04};
        when(reportService.generateBudgetXlsx(
                        eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(xlsx);

        mockMvc.perform(
                        get("/api/v1/reports/budget/xlsx")
                                .param("from", "2026-04-01")
                                .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(xlsx));
    }
}
