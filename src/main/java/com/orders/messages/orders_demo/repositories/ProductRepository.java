package com.orders.messages.orders_demo.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orders.messages.orders_demo.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsBySku(String sku);

    Optional<Product> findBySku(String sku);

}
