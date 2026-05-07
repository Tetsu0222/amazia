package com.example.delivery;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step A: Delivery Entity の永続化検証（RR-3 / R-1 / R-9）。
 * UNIQUE(sales_id) 制約 / FK 連携 / scheduled_date NULL 許容を確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class DeliveryEntityTest {

    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private SalesRepository salesRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private CustomerRepository customerRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}")
    private long pendingStatusId;

    @Value("${amazia.delivery.shipping-methods.home-delivery-id}")
    private long homeDeliveryMethodId;

    @Test
    void delivery_は注文確定時のフィールドを保存して取得できる() {
        Sales sales = persistSalesWithAddress("01967c00-aaaa-7d8e-9c5f-deadbeef0001");

        Delivery delivery = newDeliveryFor(sales, null);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);

        assertNotNull(saved.getId());
        Delivery loaded = deliveryRepository.findById(saved.getId()).orElseThrow();
        assertEquals(sales.getId(), loaded.getSalesId());
        assertEquals(pendingStatusId, loaded.getShippingStatusId());
        assertEquals(homeDeliveryMethodId, loaded.getShippingMethodId());
        assertNull(loaded.getScheduledDate());
        assertNull(loaded.getShippedDate());
        assertNull(loaded.getDeliveredDate());
        assertNull(loaded.getTrackingCode());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void deliveries_は同じsales_idを2件登録できない() {
        Sales sales = persistSalesWithAddress("01967c00-bbbb-7d8e-9c5f-deadbeef0002");
        deliveryRepository.saveAndFlush(newDeliveryFor(sales, LocalDate.of(2026, 5, 10)));

        Delivery dup = newDeliveryFor(sales, LocalDate.of(2026, 5, 11));
        assertThrows(DataIntegrityViolationException.class,
                () -> deliveryRepository.saveAndFlush(dup),
                "UNIQUE(sales_id) 制約違反となるはず");
    }

    @Test
    void findBySalesIdで関連配送を取得できる() {
        Sales sales = persistSalesWithAddress("01967c00-cccc-7d8e-9c5f-deadbeef0003");
        Delivery saved = deliveryRepository.saveAndFlush(newDeliveryFor(sales, LocalDate.of(2026, 5, 12)));

        Delivery loaded = deliveryRepository.findBySalesId(sales.getId()).orElseThrow();
        assertEquals(saved.getId(), loaded.getId());
    }

    private Sales persistSalesWithAddress(String paymentId) {
        Customer c = new Customer();
        c.setNameLast("配送");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(paymentId + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        Customer saved = customerRepository.save(c);

        Address addr = new Address();
        addr.setUserId(saved.getId());
        addr.setAddressLine("東京都千代田区千代田1-1");
        addr.setActive(true);
        Address savedAddr = addressRepository.save(addr);

        Sales s = new Sales();
        s.setUserId(saved.getId());
        s.setSkuId(1L);
        s.setQuantity(1);
        s.setAmount(1000);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(homeDeliveryMethodId);
        s.setShippingAddressId(savedAddr.getId());
        s.setShippingStatusId(pendingStatusId);
        s.setPaymentId(paymentId);
        s.setPreorder(false);
        s.setSalesDate(LocalDate.of(2026, 5, 7));
        return salesRepository.saveAndFlush(s);
    }

    private Delivery newDeliveryFor(Sales sales, LocalDate scheduledDate) {
        Delivery d = new Delivery();
        d.setSalesId(sales.getId());
        d.setShippingAddressId(sales.getShippingAddressId());
        d.setShippingMethodId(homeDeliveryMethodId);
        d.setShippingStatusId(pendingStatusId);
        d.setScheduledDate(scheduledDate);
        return d;
    }
}
