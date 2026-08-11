package com.orders.messages.orders_demo.app.product.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    boolean existsBySku(String sku);

    Optional<ProductEntity> findBySku(String sku);

}
