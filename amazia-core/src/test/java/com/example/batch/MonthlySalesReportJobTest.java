package com.example.batch;

import com.example.batch.config.BatchResult;
import com.example.batch.job.MonthlySalesReportJob;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreport.entity.MonthlySalesReport;
import com.example.salesreport.repository.MonthlySalesReportRepository;
import com.example.shared.config.TestAwsConfig;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 4-2: MonthlySalesReportJob の TDD（設計書 §3.2 ② / 計画書 §5-2）。
 *
 * <p>{@code aggregateAndPersist(YearMonth)} を直接呼んで対象月を固定し、4 軸 + 総合計が
 * UPSERT されることを検証する。R-15 の冪等性（再実行で行数が増えない）も併せて検証。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class MonthlySalesReportJobTest {

    @Autowired private MonthlySalesReportJob job;
    @Autowired private MonthlySalesReportRepository monthlyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository skuRepository;
    @Autowired private SalesRepository salesRepository;

    @Test
    void MSR_1_対象月に売上が無ければレコードは作られない() {
        YearMonth target = YearMonth.of(2030, 6);

        BatchResult result = job.aggregateAndPersist(target);

        assertEquals(0, result.targetCount());
        assertTrue(monthlyRepository.findByYearAndMonth(
                (short) target.getYear(), (short) target.getMonthValue()).isEmpty());
    }

    @Test
    void MSR_2_対象月の売上1件で4軸プラス総合計の5レコードがUPSERTされる() {
        YearMonth target = YearMonth.of(2030, 7);
        long skuId = persistSkuWithProduct();
        persistSales(skuId, target.atDay(15), 2, 5000, false);

        BatchResult result = job.aggregateAndPersist(target);

        assertTrue(result.targetCount() >= 5,
                "商品/決済/配送/予約 + 総合計の最低 5 レコード（軸 1 件ずつなので 1+1+1+1+1 = 5）");
        List<MonthlySalesReport> reports = monthlyRepository.findByYearAndMonth(
                (short) target.getYear(), (short) target.getMonthValue());
        assertEquals(result.targetCount(), reports.size());

        long totalRow = reports.stream().filter(r ->
                r.getProductId() == null && r.getPaymentMethodId() == null
                && r.getShippingMethodId() == null && r.getIsPreorder() == null).count();
        assertEquals(1L, totalRow, "総合計（NULL 軸）レコードが 1 件存在する");

        MonthlySalesReport total = reports.stream().filter(r ->
                r.getProductId() == null && r.getPaymentMethodId() == null
                && r.getShippingMethodId() == null && r.getIsPreorder() == null).findFirst().orElseThrow();
        assertEquals(5000L, total.getTotalAmount());
        assertEquals(2, total.getTotalQuantity());
    }

    @Test
    void MSR_3_R_15_同月再実行でレコード数が増えず値が上書きされる() {
        YearMonth target = YearMonth.of(2030, 8);
        long skuId = persistSkuWithProduct();
        persistSales(skuId, target.atDay(10), 1, 1000, false);

        BatchResult first = job.aggregateAndPersist(target);
        int firstCount = monthlyRepository.findByYearAndMonth(
                (short) target.getYear(), (short) target.getMonthValue()).size();

        // 売上を追加してから再実行
        persistSales(skuId, target.atDay(20), 3, 2500, false);
        BatchResult second = job.aggregateAndPersist(target);
        int secondCount = monthlyRepository.findByYearAndMonth(
                (short) target.getYear(), (short) target.getMonthValue()).size();

        assertEquals(firstCount, secondCount,
                "再実行で軸数は変わらず、値だけが上書きされる（R-15 冪等性）");

        MonthlySalesReport total = monthlyRepository.findByYearAndMonth(
                (short) target.getYear(), (short) target.getMonthValue()).stream()
                .filter(r -> r.getProductId() == null && r.getPaymentMethodId() == null
                          && r.getShippingMethodId() == null && r.getIsPreorder() == null)
                .findFirst().orElseThrow();
        assertEquals(1000L + 2500L, total.getTotalAmount());
        assertEquals(1 + 3, total.getTotalQuantity());

        assertNotNull(first); assertNotNull(second);
    }

    private long persistSkuWithProduct() {
        Product p = new Product();
        p.setName("MSR テスト商品-" + System.nanoTime());
        p.setStatusCode("ON_SALE");
        Long productId = productRepository.save(p).getId();

        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode("MSR-" + System.nanoTime());
        sku.setColor("白"); sku.setSize("M"); sku.setStatus("ACTIVE");
        return skuRepository.save(sku).getId();
    }

    private void persistSales(long skuId, LocalDate date, int quantity, int amount, boolean preorder) {
        Sales s = new Sales();
        s.setUserId(1L);
        s.setSkuId(skuId);
        s.setQuantity(quantity);
        s.setAmount(amount);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(1L);
        s.setShippingAddressId(1L);
        s.setShippingStatusId(1L);
        s.setPaymentId("MSR-PAY-" + System.nanoTime());
        s.setPreorder(preorder);
        s.setSalesDate(date);
        salesRepository.save(s);
    }
}
