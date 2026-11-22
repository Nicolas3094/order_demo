package com.orders.messages.orders_demo.app.product.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    boolean existsBySku(String sku);

    Optional<ProductEntity> findBySku(String sku);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product
            SET quantity = quantity - :requestedQuantity
            WHERE id = :productId
                AND :requestedQuantity > 0
                AND quantity >= :requestedQuantity
            """, nativeQuery = true)
    int decreaseStockIfAvailable(
            @Param("productId") UUID productId,
            @Param("requestedQuantity") long requestedQuantity);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product
            SET quantity = quantity + :requestedQuantity
            WHERE id = :productId
                AND :requestedQuantity > 0
            """, nativeQuery = true)
    int increaseStockIfAvailable(
            @Param("productId") UUID productId,
            @Param("requestedQuantity") long requestedQuantity);

}
