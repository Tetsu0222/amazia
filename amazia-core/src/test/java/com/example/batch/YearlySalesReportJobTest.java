package com.example.batch;

import com.example.batch.config.BatchResult;
import com.example.batch.job.YearlySalesReportJob;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 4-3: YearlySalesReportJob の TDD（設計書 §3.3 ① / 計画書 §5-3）。
 *
 * <p>{@code aggregateAndPersist(short)} を直接呼び、月次レポートが年単位で
 * 集約されることを検証する。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class YearlySalesReportJobTest {

    @Autowired private YearlySalesReportJob job;
    @Autowired private MonthlySalesReportRepository monthlyRepository;
    @Autowired private YearlySalesReportRepository yearlyRepository;

    @Test
    void YSR_1_対象年の月次がなければ年次レコードは作られない() {
        short year = 2031;

        BatchResult result = job.aggregateAndPersist(year);

        assertEquals(0, result.targetCount());
        assertTrue(yearlyRepository.findByYear(year).isEmpty());
    }

    @Test
    void YSR_2_2か月分の月次合計が同じ軸ならば年次に1件で集計される() {
        short year = 2032;
        // 商品 axis = productId=100 で 1 月と 2 月に同軸の月次レコードを置く
        persistMonthly(year, (short) 1, 100L, null, null, null, 5000L, 2);
        persistMonthly(year, (short) 2, 100L, null, null, null, 7000L, 3);

        BatchResult result = job.aggregateAndPersist(year);

        List<YearlySalesReport> reports = yearlyRepository.findByYear(year);
        assertEquals(1, reports.size(), "同軸 2 ヶ月分は年次 1 件に集計される");
        YearlySalesReport r = reports.get(0);
        assertEquals(5000L + 7000L, r.getTotalAmount());
        assertEquals(2 + 3, r.getTotalQuantity());
        assertEquals(100L, r.getProductId());
        assertNotNull(result);
    }

    @Test
    void YSR_3_再実行で行数が増えず値が上書きされる() {
        short year = 2033;
        persistMonthly(year, (short) 5, null, 1L, null, null, 1000L, 1);

        job.aggregateAndPersist(year);
        int firstCount = yearlyRepository.findByYear(year).size();

        // 6 月の月次を追加
        persistMonthly(year, (short) 6, null, 1L, null, null, 2500L, 4);
        job.aggregateAndPersist(year);
        int secondCount = yearlyRepository.findByYear(year).size();

        assertEquals(firstCount, secondCount, "同軸の再実行は INSERT ではなく UPDATE");
        YearlySalesReport r = yearlyRepository.findByYear(year).get(0);
        assertEquals(1000L + 2500L, r.getTotalAmount());
        assertEquals(1 + 4, r.getTotalQuantity());
    }

    private void persistMonthly(short year, short month,
                                Long productId, Long paymentMethodId,
                                Long shippingMethodId, Boolean isPreorder,
                                long amount, int quantity) {
        MonthlySalesReport r = new MonthlySalesReport();
        r.setYear(year);
        r.setMonth(month);
        r.setProductId(productId);
        r.setPaymentMethodId(paymentMethodId);
        r.setShippingMethodId(shippingMethodId);
        r.setIsPreorder(isPreorder);
        r.setTotalAmount(amount);
        r.setTotalQuantity(quantity);
        monthlyRepository.saveAndFlush(r);
    }
}
