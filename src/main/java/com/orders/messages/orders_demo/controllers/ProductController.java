package com.orders.messages.orders_demo.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orders.messages.orders_demo.dtos.request.ChangeCurrencyRequest;
import com.orders.messages.orders_demo.dtos.request.ChangeDescriptionRequest;
import com.orders.messages.orders_demo.dtos.request.ChangeNameRequest;
import com.orders.messages.orders_demo.dtos.request.ChangePriceRequest;
import com.orders.messages.orders_demo.dtos.request.CreateProductRequest;
import com.orders.messages.orders_demo.dtos.request.DecreaseStockRequest;
import com.orders.messages.orders_demo.dtos.request.IncreaseStockRequest;
import com.orders.messages.orders_demo.dtos.response.ProductResponse;
import com.orders.messages.orders_demo.mappers.ProductMapper;
import com.orders.messages.orders_demo.services.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.getProduct(productId)));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(
                productService.getAllProducts()
                        .stream()
                        .map(ProductMapper::toResponse)
                        .toList());
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest createProductRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductMapper.toResponse(
                        productService.createProduct(createProductRequest)));
    }

    @PatchMapping("/{productId}/price")
    public ResponseEntity<ProductResponse> changePrice(@PathVariable UUID productId,
            @Valid @RequestBody ChangePriceRequest request) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.changePrice(productId, request.price())));
    }

    @PatchMapping("/{productId}/increase-stock")
    public ResponseEntity<ProductResponse> increaseStock(@PathVariable UUID productId,
            @Valid @RequestBody IncreaseStockRequest request) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.increaseStock(productId, request.quantity())));
    }

    @PatchMapping("/{productId}/decrease-stock")
    public ResponseEntity<ProductResponse> decreaseStock(@PathVariable UUID productId,
            @Valid @RequestBody DecreaseStockRequest request) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.decreaseStock(productId, request.quantity())));
    }

    @PatchMapping("/{productId}/currency")
    public ResponseEntity<ProductResponse> changeCurrency(@PathVariable UUID productId,
            @Valid @RequestBody ChangeCurrencyRequest request) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.changeCurrency(productId, request.currency())));
    }

    @PatchMapping("/{productId}/name")
    public ResponseEntity<ProductResponse> changeName(@PathVariable UUID productId,
            @Valid @RequestBody ChangeNameRequest request) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.changeName(productId, request.name())));
    }

    @PatchMapping("/{productId}/description")
    public ResponseEntity<ProductResponse> changeDescription(@PathVariable UUID productId,
            @Valid @RequestBody ChangeDescriptionRequest request) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.changeDescription(productId, request.description())));
    }

    @PatchMapping("/{productId}/activate")
    public ResponseEntity<ProductResponse> activate(@PathVariable UUID productId) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.activate(productId)));
    }

    @PatchMapping("/{productId}/deactivate")
    public ResponseEntity<ProductResponse> deactivate(@PathVariable UUID productId) {
        return ResponseEntity.ok(
                ProductMapper.toResponse(
                        productService.deactivate(productId)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }
}
