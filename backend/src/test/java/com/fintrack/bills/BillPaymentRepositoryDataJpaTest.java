package com.fintrack.bills;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class BillPaymentRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired BillPaymentRepository repo;
    @Autowired BillRepository billRepo;
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

    private Bill seedBill(UUID userId, String name) {
        return billRepo.save(
                Bill.builder()
                        .userId(userId)
                        .name(name)
                        .amount(new BigDecimal("100"))
                        .dueDay(15)
                        .build());
    }

    private BillPayment payment(UUID billId, String period, BillPayment.PaymentStatus status) {
        return BillPayment.builder()
                .billId(billId)
                .period(period)
                .amount(new BigDecimal("100"))
                .status(status)
                .build();
    }

    @Test
    void findByBillIdOrdersPeriodDesc() {
        UUID userId = seedUser("ali");
        Bill bill = seedBill(userId, "Internet");
        repo.save(payment(bill.getId(), "2026-03", BillPayment.PaymentStatus.PAID));
        repo.save(payment(bill.getId(), "2026-05", BillPayment.PaymentStatus.PAID));
        repo.save(payment(bill.getId(), "2026-04", BillPayment.PaymentStatus.PAID));

        assertThat(repo.findByBillIdOrderByPeriodDesc(bill.getId()))
                .extracting(BillPayment::getPeriod)
                .containsExactly("2026-05", "2026-04", "2026-03");
    }

    @Test
    void findTop2ByBillIdAndStatusReturnsLatestPaid() {
        UUID userId = seedUser("ada");
        Bill bill = seedBill(userId, "Phone");
        repo.save(payment(bill.getId(), "2026-01", BillPayment.PaymentStatus.PAID));
        repo.save(payment(bill.getId(), "2026-02", BillPayment.PaymentStatus.PAID));
        repo.save(payment(bill.getId(), "2026-03", BillPayment.PaymentStatus.PAID));
        repo.save(payment(bill.getId(), "2026-04", BillPayment.PaymentStatus.PENDING));

        var top2 =
                repo.findTop2ByBillIdAndStatusOrderByPeriodDesc(
                        bill.getId(), BillPayment.PaymentStatus.PAID);

        assertThat(top2)
                .extracting(BillPayment::getPeriod)
                .containsExactly("2026-03", "2026-02");
    }

    @Test
    void findByBillIdAndPeriodReturnsExactMatch() {
        UUID userId = seedUser("kemal");
        Bill bill = seedBill(userId, "Power");
        repo.save(payment(bill.getId(), "2026-04", BillPayment.PaymentStatus.PAID));

        assertThat(repo.findByBillIdAndPeriod(bill.getId(), "2026-04")).isPresent();
        assertThat(repo.findByBillIdAndPeriod(bill.getId(), "2026-99")).isEmpty();
    }
}
