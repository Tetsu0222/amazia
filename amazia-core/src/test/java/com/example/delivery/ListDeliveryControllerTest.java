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
 * フェーズ15 Step B-6-α: GET /api/deliveries の検証。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ListDeliveryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private SalesRepository salesRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;

    @Value("${amazia.sales.shipping-statuses.pending-id}")  private long pendingId;
    @Value("${amazia.sales.shipping-statuses.shipped-id}")  private long shippedId;
    @Value("${amazia.delivery.shipping-methods.home-delivery-id}") private long homeDeliveryId;

    @Test
    void 全件一覧を取得できる() throws Exception {
        persistDeliveryWithSales(pendingId, "01967c61-aaaa-7d8e-9c5f-deadbeef0001");
        persistDeliveryWithSales(shippedId, "01967c61-bbbb-7d8e-9c5f-deadbeef0002");

        mockMvc.perform(get("/api/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void shippingStatusIdフィルタで絞り込める() throws Exception {
        persistDeliveryWithSales(pendingId, "01967c61-cccc-7d8e-9c5f-deadbeef0003");
        persistDeliveryWithSales(shippedId, "01967c61-dddd-7d8e-9c5f-deadbeef0004");

        mockMvc.perform(get("/api/deliveries?shippingStatusId=" + shippedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].shippingStatusId",
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is((int) shippedId))));
    }

    private void persistDeliveryWithSales(Long shippingStatusId, String paymentId) {
        Customer c = new Customer();
        c.setNameLast("一覧");
        c.setNameFirst("テスト");
        c.setPostalCode("1000001");
        c.setAddress("東京都千代田区千代田1-1");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("list-dlv-" + System.nanoTime() + "@example.com");
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
        s.setShippingStatusId(shippingStatusId);
        s.setPaymentId(paymentId);
        s.setPreorder(false);
        s.setSalesDate(LocalDate.of(2026, 5, 7));
        Sales savedSales = salesRepository.saveAndFlush(s);

        Delivery d = new Delivery();
        d.setSalesId(savedSales.getId());
        d.setShippingAddressId(addressId);
        d.setShippingMethodId(homeDeliveryId);
        d.setShippingStatusId(shippingStatusId);
        deliveryRepository.saveAndFlush(d);
    }
}
