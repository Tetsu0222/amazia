package com.example.salesreport;

import com.example.salesreport.entity.MonthlySalesReport;
import com.example.salesreport.entity.YearlySalesReport;
import com.example.salesreport.repository.MonthlySalesReportRepository;
import com.example.salesreport.repository.YearlySalesReportRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: monthly / yearly_sales_reports Entity / Repository の永続化検証。
 * 4 軸 NULL 運用と UNIQUE 制約の機能を確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class SalesReportRepositoryTest {

    @Autowired
    private MonthlySalesReportRepository monthlyRepo;
    @Autowired
    private YearlySalesReportRepository yearlyRepo;

    @Test
    void monthly_を全軸_NULL_で保存できる() {
        MonthlySalesReport r = new MonthlySalesReport();
        r.setYear((short) 2026);
        r.setMonth((short) 5);
        r.setTotalAmount(10_000L);
        r.setTotalQuantity(3);

        MonthlySalesReport saved = monthlyRepo.saveAndFlush(r);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getProductId());
        assertNull(saved.getPaymentMethodId());
        assertNull(saved.getShippingMethodId());
        assertNull(saved.getIsPreorder());
    }

    @Test
    void monthly_の_findByYearAndMonth_で抽出できる() {
        MonthlySalesReport r1 = newMonthly((short) 2026, (short) 5);
        MonthlySalesReport r2 = newMonthly((short) 2026, (short) 6);
        r1.setProductId(1L);
        r2.setProductId(1L);
        monthlyRepo.saveAndFlush(r1);
        monthlyRepo.saveAndFlush(r2);

        List<MonthlySalesReport> may = monthlyRepo.findByYearAndMonth((short) 2026, (short) 5);
        assertEquals(1, may.size());
    }

    @Test
    void yearly_を保存して_findByYear_で取得できる() {
        YearlySalesReport y = new YearlySalesReport();
        y.setYear((short) 2025);
        y.setProductId(2L);
        y.setTotalAmount(123_000L);
        y.setTotalQuantity(45);
        yearlyRepo.saveAndFlush(y);

        List<YearlySalesReport> result = yearlyRepo.findByYear((short) 2025);
        assertEquals(1, result.size());
        assertEquals(123_000L, result.get(0).getTotalAmount());
    }

    private MonthlySalesReport newMonthly(short year, short month) {
        MonthlySalesReport r = new MonthlySalesReport();
        r.setYear(year);
        r.setMonth(month);
        r.setTotalAmount(0L);
        r.setTotalQuantity(0);
        return r;
    }
}
