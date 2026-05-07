package com.example.delivery;

import com.example.address.entity.Address;
import com.example.address.repository.AddressRepository;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shared.config.TestAwsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ15 Step B-6-β: PATCH /api/deliveries/{id}/scheduled-date の検証（RRR-5）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UpdateScheduledDateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}") private long pendingId;
    @Value("${amazia.delivery.shipping-methods.home-delivery-id}") private long homeDeliveryId;
    @Value("${amazia.delivery.scheduled-date-reasons.manual}") private String manualPrefix;

    @Test
    void 手動更新時comment先頭に_manual_プレフィックスが自動付与される() throws Exception {
        Long deliveryId = persistDelivery("01967c72-aaaa-7d8e-9c5f-deadbeef0001");

        Map<String, Object> body = new HashMap<>();
        body.put("scheduledDate", LocalDate.of(2026, 5, 15).toString());
        body.put("reason", "出荷遅延");

        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/scheduled-date")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Delivery d = deliveryRepository.findById(deliveryId).orElseThrow();
        assertEquals(LocalDate.of(2026, 5, 15), d.getScheduledDate());

        List<OperationLog> logs = operationLogRepository.findAll().stream()
                .filter(l -> "update_scheduled_date".equals(l.getAction())
                          && deliveryId.equals(l.getTargetId()))
                .toList();
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getComment().startsWith(manualPrefix),
                "comment 先頭は " + manualPrefix + " プレフィックス: " + logs.get(0).getComment());
    }

    @Test
    void scheduledDate欠落で422を返す() throws Exception {
        Long deliveryId = persistDelivery("01967c72-bbbb-7d8e-9c5f-deadbeef0002");

        Map<String, Object> body = new HashMap<>();
        // scheduledDate 欠落

        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/scheduled-date")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    private Long persistDelivery(String paymentId) {
        Customer c = new Customer();
        c.setNameLast("予定日");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("sched-api-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        Long customerId = customerRepository.save(c).getId();

        Address a = new Address();
        a.setUserId(customerId);
        a.setAddressLine("東京都千代田区千代田1-1");
        a.setActive(true);
        Long addressId = addressRepository.save(a).getId();

        Sales s = new Sales();
        s.setUserId(customerId);
        s.setSkuId(1L);
        s.setQuantity(1);
        s.setAmount(1000);
        s.setPaymentMethodId(1L);
        s.setShippingMethodId(homeDeliveryId);
        s.setShippingAddressId(addressId);
        s.setShippingStatusId(pendingId);
        s.setPaymentId(paymentId);
        s.setPreorder(false);
        s.setSalesDate(LocalDate.of(2026, 5, 7));
        Sales savedSales = salesRepository.saveAndFlush(s);

        Delivery d = new Delivery();
        d.setSalesId(savedSales.getId());
        d.setShippingAddressId(addressId);
        d.setShippingMethodId(homeDeliveryId);
        d.setShippingStatusId(pendingId);
        d.setScheduledDate(LocalDate.of(2026, 5, 10));
        return deliveryRepository.saveAndFlush(d).getId();
    }
}
