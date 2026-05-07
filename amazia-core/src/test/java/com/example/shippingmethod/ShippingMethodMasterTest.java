package com.example.shippingmethod;

import com.example.shared.config.TestAwsConfig;
import com.example.shippingmethod.entity.ShippingMethod;
import com.example.shippingmethod.repository.ShippingMethodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ15 Step A: shipping_methods マスタ初期投入の検証。
 * test-data.sql で home_delivery / konbini_pickup / dropoff の 3 件が投入されることを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class ShippingMethodMasterTest {

    @Autowired
    private ShippingMethodRepository repository;

    @Value("${amazia.delivery.shipping-methods.home-delivery-id}")
    private long homeDeliveryId;

    @Value("${amazia.delivery.shipping-methods.konbini-pickup-id}")
    private long konbiniPickupId;

    @Value("${amazia.delivery.shipping-methods.dropoff-id}")
    private long dropoffId;

    @Test
    void shipping_methods_マスタが3件投入されている() {
        assertEquals(3, repository.count());
    }

    @Test
    void config_経由のIDで配送方法を取得できる() {
        ShippingMethod home = repository.findById(homeDeliveryId).orElseThrow();
        assertEquals("home_delivery", home.getName());

        ShippingMethod konbini = repository.findById(konbiniPickupId).orElseThrow();
        assertEquals("konbini_pickup", konbini.getName());

        ShippingMethod dropoff = repository.findById(dropoffId).orElseThrow();
        assertEquals("dropoff", dropoff.getName());
    }
}
