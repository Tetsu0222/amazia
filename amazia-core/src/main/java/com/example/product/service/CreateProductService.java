package com.example.product.service;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.product.validator.ProductStatusValidator;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class CreateProductService {

    private final ProductRepository repository;
    private final ProductStatusValidator statusValidator;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuStockRepository stockRepository;

    public CreateProductService(ProductRepository repository,
                                ProductStatusValidator statusValidator,
                                ProductSkuRepository skuRepository,
                                ProductSkuPriceRepository priceRepository,
                                ProductSkuStockRepository stockRepository) {
        this.repository = repository;
        this.statusValidator = statusValidator;
        this.skuRepository = skuRepository;
        this.priceRepository = priceRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public Product create(Product product) {
        statusValidator.validate(product.getStatusCode());
        Product saved = repository.save(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(saved.getId());
        sku.setSkuCode("DEFAULT-" + saved.getId());
        sku.setColor("DEFAULT");
        sku.setSize("FREE");
        ProductSku savedSku = skuRepository.save(sku);

        if (saved.getPrice() != null) {
            ProductSkuPrice price = new ProductSkuPrice();
            price.setSkuId(savedSku.getId());
            price.setPrice(saved.getPrice());
            price.setStartDate(LocalDate.now());
            priceRepository.save(price);
        }

        ProductSkuStock stock = new ProductSkuStock();
        stock.setSkuId(savedSku.getId());
        stock.setQuantity(saved.getStock() != null ? saved.getStock() : 0);
        stockRepository.save(stock);

        return saved;
    }
}
