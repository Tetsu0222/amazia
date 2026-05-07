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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ15 Step B-6-β: PATCH /api/deliveries/{id}/address の検証（RRR-7）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UpdateShippingAddressControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}")  private long pendingId;
    @Value("${amazia.delivery.shipping-methods.home-delivery-id}") private long homeDeliveryId;

    @Test
    void 同じユーザの別住所への変更は成功し_operation_logsに記録される() throws Exception {
        Setup s = setup("01967c71-aaaa-7d8e-9c5f-deadbeef0001");
        // 同じユーザの別住所
        Long newAddressId = persistAddress(s.customerId, true);

        Map<String, Object> body = new HashMap<>();
        body.put("shippingAddressId", newAddressId);
        body.put("reason", "ユーザ希望");

        mockMvc.perform(patch("/api/deliveries/" + s.deliveryId + "/address")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Delivery d = deliveryRepository.findById(s.deliveryId).orElseThrow();
        assertEquals(newAddressId, d.getShippingAddressId());

        List<OperationLog> logs = operationLogRepository.findAll().stream()
                .filter(l -> "update_shipping_address".equals(l.getAction())
                          && s.deliveryId.equals(l.getTargetId()))
                .toList();
        assertEquals(1, logs.size());
    }

    @Test
    void 別ユーザの住所を指定すると403が返る() throws Exception {
        Setup s = setup("01967c71-bbbb-7d8e-9c5f-deadbeef0002");

        // 別ユーザを作って住所を持たせる
        Customer other = new Customer();
        other.setNameLast("他人");
        other.setNameFirst("テスト");
        other.setPostalCode("1500001");
        other.setAddress("東京都渋谷区神宮前1-1");
        other.setBirthday(LocalDate.of(1990, 1, 1));
        other.setEmail("other-" + System.nanoTime() + "@example.com");
        other.setPasswordHash("dummy");
        other.setPaymentMethod("credit_card");
        other.setActiveFlag(true);
        Long otherCustomerId = customerRepository.save(other).getId();
        Long otherAddressId = persistAddress(otherCustomerId, true);

        Map<String, Object> body = new HashMap<>();
        body.put("shippingAddressId", otherAddressId);

        mockMvc.perform(patch("/api/deliveries/" + s.deliveryId + "/address")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void is_active_false_な住所は400で拒否される() throws Exception {
        Setup s = setup("01967c71-cccc-7d8e-9c5f-deadbeef0003");
        Long inactiveAddressId = persistAddress(s.customerId, false);

        Map<String, Object> body = new HashMap<>();
        body.put("shippingAddressId", inactiveAddressId);

        mockMvc.perform(patch("/api/deliveries/" + s.deliveryId + "/address")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private record Setup(Long customerId, Long deliveryId) {}

    private Setup setup(String paymentId) {
        Customer c = new Customer();
        c.setNameLast("住所変更");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("addr-api-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummy");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        Long customerId = customerRepository.save(c).getId();

        Long addressId = persistAddress(customerId, true);

        Sales sales = new Sales();
        sales.setUserId(customerId);
        sales.setSkuId(1L);
        sales.setQuantity(1);
        sales.setAmount(1000);
        sales.setPaymentMethodId(1L);
        sales.setShippingMethodId(homeDeliveryId);
        sales.setShippingAddressId(addressId);
        sales.setShippingStatusId(pendingId);
        sales.setPaymentId(paymentId);
        sales.setPreorder(false);
        sales.setSalesDate(LocalDate.of(2026, 5, 7));
        Sales savedSales = salesRepository.saveAndFlush(sales);

        Delivery d = new Delivery();
        d.setSalesId(savedSales.getId());
        d.setShippingAddressId(addressId);
        d.setShippingMethodId(homeDeliveryId);
        d.setShippingStatusId(pendingId);
        Long deliveryId = deliveryRepository.saveAndFlush(d).getId();

        return new Setup(customerId, deliveryId);
    }

    private Long persistAddress(Long userId, boolean active) {
        Address a = new Address();
        a.setUserId(userId);
        a.setAddressLine("住所" + System.nanoTime());
        a.setActive(active);
        return addressRepository.save(a).getId();
    }
}
