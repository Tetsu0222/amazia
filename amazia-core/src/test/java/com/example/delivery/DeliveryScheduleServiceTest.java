package com.example.delivery;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.repository.ShippingLeadTimeRepository;
import com.example.delivery.service.DeliveryScheduleService;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.sales.entity.Sales;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * フェーズX-5：{@link DeliveryScheduleService#calculate} の都道府県別リードタイム反映検証。
 *
 * <p>schema.sql で投入済の 47×3=141 行の {@code shipping_lead_times} マスタを使い、
 * 厳格4県（北海道 / 長崎県 / 鹿児島県 / 沖縄県）の +2 加算と config フォールバックを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class DeliveryScheduleServiceTest {

    @Autowired private DeliveryScheduleService scheduleService;
    @Autowired private ShippingLeadTimeRepository shippingLeadTimeRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;

    @Value("${amazia.delivery.shipping-methods.home-delivery-id}")  private long homeDeliveryId;
    @Value("${amazia.delivery.shipping-methods.konbini-pickup-id}") private long konbiniPickupId;
    @Value("${amazia.delivery.shipping-methods.dropoff-id}")        private long dropoffId;
    @Value("${amazia.delivery.lead-time-days.home-delivery}")  private int homeDeliveryDays;
    @Value("${amazia.delivery.lead-time-days.konbini-pickup}") private int konbiniPickupDays;

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 5, 7);

    @BeforeEach
    void verifyMasterData() {
        // フェーズ X-5：47 都道府県 × 3 配送方法 = 141 行が投入されていること
        assertEquals(141L, shippingLeadTimeRepository.count(),
                "shipping_lead_times に 47 都道府県 × 3 配送方法 = 141 行が投入されているべき");
    }

    @Test
    void 東京都_home_deliveryは標準値の3日でsalesDate_plus3を返す() {
        Sales sales = sales(homeDeliveryId, persistAddress("東京都"));
        LocalDate result = scheduleService.calculate(sales, 10);
        assertEquals(ORDER_DATE.plusDays(3), result);
    }

    @Test
    void 北海道_home_deliveryは離島加算で5日を返す() {
        Sales sales = sales(homeDeliveryId, persistAddress("北海道"));
        LocalDate result = scheduleService.calculate(sales, 10);
        assertEquals(ORDER_DATE.plusDays(5), result);
    }

    @Test
    void 沖縄県_konbini_pickupは離島加算で6日を返す() {
        Sales sales = sales(konbiniPickupId, persistAddress("沖縄県"));
        LocalDate result = scheduleService.calculate(sales, 10);
        assertEquals(ORDER_DATE.plusDays(6), result);
    }

    @Test
    void 鹿児島県と長崎県も厳格4県として_2日加算される() {
        assertEquals(ORDER_DATE.plusDays(5),
                scheduleService.calculate(sales(homeDeliveryId, persistAddress("鹿児島県")), 10));
        assertEquals(ORDER_DATE.plusDays(5),
                scheduleService.calculate(sales(homeDeliveryId, persistAddress("長崎県")), 10));
    }

    @Test
    void マスタ未登録の都道府県名はconfigフォールバックを使う() {
        // "海外" は schema.sql の初期 141 行に存在しない → config フォールバック
        Sales sales = sales(homeDeliveryId, persistAddress("海外"));
        LocalDate result = scheduleService.calculate(sales, 10);
        assertEquals(ORDER_DATE.plusDays(homeDeliveryDays), result);
    }

    @Test
    void prefectureがnullの場合はconfigフォールバックを使う() {
        Sales sales = sales(konbiniPickupId, persistAddress(null));
        LocalDate result = scheduleService.calculate(sales, 10);
        assertEquals(ORDER_DATE.plusDays(konbiniPickupDays), result);
    }

    @Test
    void prefectureが空文字の場合もconfigフォールバックを使う() {
        Sales sales = sales(homeDeliveryId, persistAddress(""));
        LocalDate result = scheduleService.calculate(sales, 10);
        assertEquals(ORDER_DATE.plusDays(homeDeliveryDays), result);
    }

    @Test
    void prefecture文字列が厳密不一致のときはconfigフォールバックを使う() {
        // マスタは "東京都"。Address に "東京" と入っている厳密不一致ケース
        Sales sales = sales(homeDeliveryId, persistAddress("東京"));
        LocalDate result = scheduleService.calculate(sales, 10);
        assertEquals(ORDER_DATE.plusDays(homeDeliveryDays), result);
    }

    @Test
    void 在庫不足の場合は従来通りnullを返す() {
        Sales sales = sales(homeDeliveryId, persistAddress("東京都"));
        sales.setQuantity(20);
        assertNull(scheduleService.calculate(sales, 5));
    }

    private Long persistAddress(String prefecture) {
        Customer c = new Customer();
        c.setNameLast("配送");
        c.setNameFirst("X-5");
        c.setPostalCode("1000001");
        c.setAddress("ダミー");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("phasex5-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        Long customerId = customerRepository.save(c).getId();

        Address a = new Address();
        a.setUserId(customerId);
        a.setPrefecture(prefecture);
        a.setAddressLine("ダミー番地");
        a.setActive(true);
        return addressRepository.save(a).getId();
    }

    private Sales sales(long shippingMethodId, Long addressId) {
        Sales s = new Sales();
        s.setQuantity(1);
        s.setShippingMethodId(shippingMethodId);
        s.setShippingAddressId(addressId);
        s.setSalesDate(ORDER_DATE);
        return s;
    }
}
