package com.example.market.cart.repository;

import com.example.market.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartIdOrderByAddedAtAsc(Long cartId);

    Optional<CartItem> findByCartIdAndSkuIdAndPreorder(Long cartId, Long skuId, boolean preorder);

    void deleteByCartId(Long cartId);
}
