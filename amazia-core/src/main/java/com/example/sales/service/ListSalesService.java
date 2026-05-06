package com.example.sales.service;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.paymentmethod.entity.PaymentMethod;
import com.example.paymentmethod.repository.PaymentMethodRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.dto.AdminSalesItem;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shippingstatus.entity.ShippingStatus;
import com.example.shippingstatus.repository.ShippingStatusRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Console: 管理画面向けの売上一覧取得 Service。
 *
 * 設計書 r4 / Amazia Console §売上管理。
 * sales 全件を取得し、market_customers / product_skus / products / shipping_statuses /
 * payment_methods を JOIN して {@link AdminSalesItem} に整形する。
 *
 * 集計はせず、画面表示のための整形のみを行う（フィルタ・並び替え・集計は Console 側で実装）。
 * 大量データ時のページング対応は将来課題（phase15 以降の運用と合わせて検討）。
 */
@Service
public class ListSalesService {

    private final SalesRepository salesRepository;
    private final CustomerRepository customerRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final ShippingStatusRepository shippingStatusRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    public ListSalesService(SalesRepository salesRepository,
                            CustomerRepository customerRepository,
                            ProductSkuRepository skuRepository,
                            ProductRepository productRepository,
                            ShippingStatusRepository shippingStatusRepository,
                            PaymentMethodRepository paymentMethodRepository) {
        this.salesRepository = salesRepository;
        this.customerRepository = customerRepository;
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
        this.shippingStatusRepository = shippingStatusRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminSalesItem> list() {
        List<Sales> salesList = salesRepository.findAllByOrderBySalesDateDescIdDesc();
        if (salesList.isEmpty()) return List.of();

        Set<Long> customerIds = salesList.stream().map(Sales::getUserId).collect(Collectors.toSet());
        Map<Long, Customer> customerMap = new HashMap<>();
        customerRepository.findAllById(customerIds).forEach(c -> customerMap.put(c.getId(), c));

        Set<Long> skuIds = salesList.stream().map(Sales::getSkuId).collect(Collectors.toSet());
        List<ProductSku> skus = skuRepository.findAllById(skuIds);
        Map<Long, ProductSku> skuMap = skus.stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));

        Set<Long> productIds = skus.stream().map(ProductSku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = new HashMap<>();
        productRepository.findAllById(productIds).forEach(p -> productMap.put(p.getId(), p));

        Set<Long> statusIds = salesList.stream().map(Sales::getShippingStatusId).collect(Collectors.toSet());
        Map<Long, ShippingStatus> statusMap = new HashMap<>();
        shippingStatusRepository.findAllById(statusIds).forEach(s -> statusMap.put(s.getId(), s));

        Set<Long> paymentMethodIds = salesList.stream().map(Sales::getPaymentMethodId).collect(Collectors.toSet());
        Map<Long, PaymentMethod> paymentMethodMap = new HashMap<>();
        paymentMethodRepository.findAllById(paymentMethodIds).forEach(p -> paymentMethodMap.put(p.getId(), p));

        return salesList.stream().map(s -> {
            Customer customer = customerMap.get(s.getUserId());
            ProductSku sku = skuMap.get(s.getSkuId());
            Product product = sku != null ? productMap.get(sku.getProductId()) : null;
            ShippingStatus status = statusMap.get(s.getShippingStatusId());
            PaymentMethod paymentMethod = paymentMethodMap.get(s.getPaymentMethodId());
            return new AdminSalesItem(
                    s.getId(),
                    s.getSalesDate(),
                    s.getShippingDate(),
                    s.getUserId(),
                    customer != null ? customer.getNameLast() + " " + customer.getNameFirst() : null,
                    s.getSkuId(),
                    product != null ? product.getName() : null,
                    sku != null ? sku.getColor() : null,
                    sku != null ? sku.getSize() : null,
                    s.getQuantity(),
                    s.getAmount(),
                    status != null ? status.getCode() : null,
                    s.getShippingMethodId(),
                    s.getPaymentMethodId(),
                    paymentMethod != null ? paymentMethod.getName() : null,
                    s.isPreorder()
            );
        }).toList();
    }
}
