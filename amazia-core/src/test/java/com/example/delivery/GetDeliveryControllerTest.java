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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ15 Step B-6-α: GET /api/deliveries/{id} の検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetDeliveryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}") private long pendingId;
    @Value("${amazia.delivery.shipping-methods.home-delivery-id}") private long homeDeliveryId;

    @Test
    void 既存配送をJSONで取得できる() throws Exception {
        Long deliveryId = persistDeliveryWithSales("01967c60-aaaa-7d8e-9c5f-deadbeef0001");

        mockMvc.perform(get("/api/deliveries/" + deliveryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId))
                .andExpect(jsonPath("$.shippingStatusId").value(pendingId))
                .andExpect(jsonPath("$.shippingMethodId").value(homeDeliveryId));
    }

    @Test
    void 存在しないIDは404を返す() throws Exception {
        mockMvc.perform(get("/api/deliveries/999999"))
                .andExpect(status().isNotFound());
    }

    private Long persistDeliveryWithSales(String paymentId) {
        Customer c = new Customer();
        c.setNameLast("テスト");
        c.setNameFirst("購入者");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("get-dlv-" + System.nanoTime() + "@example.com");
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
        return deliveryRepository.saveAndFlush(d).getId();
    }
}
