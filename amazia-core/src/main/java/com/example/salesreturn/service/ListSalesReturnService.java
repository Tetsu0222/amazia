package com.example.salesreturn.service;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.salesreturn.dto.AdminSalesReturnItem;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.repository.SalesReturnRepository;
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
 * Console: 管理画面向けの返品申請一覧取得 Service（B-5-4）。
 *
 * 設計書 r4 / Amazia Console §返品管理。
 * sales_return 全件を取得し、sales / market_customers / product_skus / products を JOIN して
 * {@link AdminSalesReturnItem} に整形する。
 *
 * 集計はせず、画面表示のための整形のみを行う（フィルタ・並び替えは Console 側で実装）。
 * 大量データ時のページング対応は将来課題（運用と合わせて検討）。
 */
@Service
public class ListSalesReturnService {

    private final SalesReturnRepository salesReturnRepository;
    private final SalesRepository salesRepository;
    private final CustomerRepository customerRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductRepository productRepository;

    public ListSalesReturnService(SalesReturnRepository salesReturnRepository,
                                  SalesRepository salesRepository,
                                  CustomerRepository customerRepository,
                                  ProductSkuRepository skuRepository,
                                  ProductRepository productRepository) {
        this.salesReturnRepository = salesReturnRepository;
        this.salesRepository = salesRepository;
        this.customerRepository = customerRepository;
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminSalesReturnItem> list() {
        List<SalesReturn> returns = salesReturnRepository.findAllByOrderByCreatedAtDescIdDesc();
        if (returns.isEmpty()) return List.of();

        Set<Long> salesIds = returns.stream().map(SalesReturn::getSalesId).collect(Collectors.toSet());
        List<Sales> salesList = salesRepository.findAllById(salesIds);
        Map<Long, Sales> salesMap = salesList.stream()
                .collect(Collectors.toMap(Sales::getId, s -> s));

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

        return returns.stream().map(ret -> {
            Sales sales = salesMap.get(ret.getSalesId());
            Customer customer = sales != null ? customerMap.get(sales.getUserId()) : null;
            ProductSku sku = sales != null ? skuMap.get(sales.getSkuId()) : null;
            Product product = sku != null ? productMap.get(sku.getProductId()) : null;
            return new AdminSalesReturnItem(
                    ret.getId(),
                    ret.getStatus(),
                    ret.getQuantity(),
                    ret.getReason(),
                    ret.getCreatedAt(),
                    ret.getApprovedAt(),
                    ret.getApproverId(),
                    sales != null ? sales.getId() : null,
                    sales != null ? sales.getSalesDate() : null,
                    sales != null ? sales.getUserId() : null,
                    customer != null ? customer.getNameLast() + " " + customer.getNameFirst() : null,
                    sales != null ? sales.getSkuId() : null,
                    product != null ? product.getName() : null,
                    sku != null ? sku.getColor() : null,
                    sku != null ? sku.getSize() : null
            );
        }).toList();
    }
}
