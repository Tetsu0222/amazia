package com.example.inquiry.validator;

import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.inquiry.exception.ForbiddenInquiryAccessException;
import com.example.inquiry.exception.InquiryNotFoundException;
import com.example.inquiry.exception.InquiryValidationException;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * フェーズ18 Step 2: InquiryTargetOwnershipValidator の所有者検証ロジック単体テスト。
 * 設計書 §11.1 OWNV-1 〜 OWNV-8 の対応。
 */
@ExtendWith(MockitoExtension.class)
class InquiryTargetOwnershipValidatorTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private SalesRepository salesRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private InquiryTargetOwnershipValidator validator;

    private static final long CUSTOMER_ID = 100L;
    private static final long OTHER_CUSTOMER_ID = 200L;

    @BeforeEach
    void setUp() {
        // no-op（Mockito が initMocks 済み）
    }

    @Test
    void OWNV1_target_type_null_かつ_target_id_null_は_OK() {
        assertDoesNotThrow(() -> validator.validate(null, null, CUSTOMER_ID));
    }

    @Test
    void OWNV1b_target_type_null_かつ_target_id_あり_は_InquiryValidationException() {
        assertThrows(InquiryValidationException.class,
                () -> validator.validate(null, 1L, CUSTOMER_ID));
    }

    @Test
    void OWNV2_delivery_自分のものは_OK() {
        Delivery d = new Delivery();
        d.setSalesId(50L);
        Sales s = newSales(CUSTOMER_ID);
        when(deliveryRepository.findById(10L)).thenReturn(Optional.of(d));
        when(salesRepository.findById(50L)).thenReturn(Optional.of(s));

        assertDoesNotThrow(() -> validator.validate("delivery", 10L, CUSTOMER_ID));
    }

    @Test
    void OWNV3_delivery_他人のものは_ForbiddenInquiryAccessException() {
        Delivery d = new Delivery();
        d.setSalesId(50L);
        Sales s = newSales(OTHER_CUSTOMER_ID);
        when(deliveryRepository.findById(10L)).thenReturn(Optional.of(d));
        when(salesRepository.findById(50L)).thenReturn(Optional.of(s));

        assertThrows(ForbiddenInquiryAccessException.class,
                () -> validator.validate("delivery", 10L, CUSTOMER_ID));
    }

    @Test
    void OWNV4_sales_他人のものは_ForbiddenInquiryAccessException() {
        Sales s = newSales(OTHER_CUSTOMER_ID);
        when(salesRepository.findById(50L)).thenReturn(Optional.of(s));

        assertThrows(ForbiddenInquiryAccessException.class,
                () -> validator.validate("sales", 50L, CUSTOMER_ID));
    }

    @Test
    void OWNV5_product_active_は_OK() {
        Product p = newProduct(true);
        when(productRepository.findById(7L)).thenReturn(Optional.of(p));

        assertDoesNotThrow(() -> validator.validate("product", 7L, CUSTOMER_ID));
    }

    @Test
    void OWNV6_product_inactive_は_InquiryValidationException() {
        Product p = newProduct(false);
        when(productRepository.findById(7L)).thenReturn(Optional.of(p));

        assertThrows(InquiryValidationException.class,
                () -> validator.validate("product", 7L, CUSTOMER_ID));
    }

    @Test
    void OWNV7_delivery_存在しないは_InquiryNotFoundException() {
        when(deliveryRepository.findById(999_999L)).thenReturn(Optional.empty());

        assertThrows(InquiryNotFoundException.class,
                () -> validator.validate("delivery", 999_999L, CUSTOMER_ID));
    }

    @Test
    void OWNV8_unknown_target_type_は_InquiryValidationException() {
        assertThrows(InquiryValidationException.class,
                () -> validator.validate("unknown_value", 1L, CUSTOMER_ID));
    }

    @Test
    void OWNV9_target_type_あり_かつ_target_id_null_は_InquiryValidationException() {
        assertThrows(InquiryValidationException.class,
                () -> validator.validate("delivery", null, CUSTOMER_ID));
    }

    private Sales newSales(Long userId) {
        Sales s = new Sales();
        s.setUserId(userId);
        return s;
    }

    private Product newProduct(boolean active) {
        Product p = new Product();
        p.setActive(active);
        return p;
    }
}
