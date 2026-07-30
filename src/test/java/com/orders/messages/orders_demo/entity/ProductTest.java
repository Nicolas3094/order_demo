package com.orders.messages.orders_demo.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.exceptions.product.InsufficientStockException;
import com.orders.messages.orders_demo.exceptions.product.InvalidProductException;

public class ProductTest {
    private Product product;

    @BeforeEach
    public void setup() {
        product = createProduct();
    }

    @Test
    public void activate_WhenProductIsInactive_ShouldActivateProduct() {
        product.deactivate();

        product.activate();

        assertEquals(true, product.getActive());
    }

    @Test
    public void deactivate_WhenProductIsActive_ShouldDeactivateProduct() {
        product.deactivate();

        assertEquals(false, product.getActive());
    }

    @Test
    public void changePrice_WhenPriceIsPositive_ShouldUpdatePrice() {
        BigDecimal newPrice = new BigDecimal("250.00");

        product.changePrice(newPrice);

        assertEquals(newPrice, product.getPrice());
    }

    @Test
    public void changePrice_WhenPriceIsNegative_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> product.changePrice(new BigDecimal("-10.00")));

        assertEquals("Price must be positive.", result.getMessage());
    }

    @Test
    public void changeName_WhenNameIsValid_ShouldUpdateName() {
        product.changeName("New Name");

        assertEquals("New Name", product.getName());
    }

    @Test
    public void changeName_WhenNameIsBlank_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> product.changeName(""));

        assertEquals("Name must not be empty.", result.getMessage());
    }

    @Test
    public void changeDescription_WhenDescriptionIsValid_ShouldUpdateDescription() {
        product.changeDescription("New Description");

        assertEquals("New Description", product.getDescription());
    }

    @Test
    public void changeDescription_WhenDescriptionIsBlank_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> product.changeDescription(""));

        assertEquals("Description must not be empty.", result.getMessage());
    }

    @Test
    public void changeCurrency_WhenCurrencyIsValid_ShouldUpdateCurrency() {
        product.changeCurrency(Currency.USD);

        assertEquals(Currency.USD, product.getCurrency());
    }

    @Test
    public void increaseStock_WhenQuantityIsPositive_ShouldIncreaseStock() {
        product.increaseStock(10);

        assertEquals(20L, product.getQuantity());
    }

    @Test
    public void increaseStock_WhenQuantityIsZero_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> product.increaseStock(0));

        assertEquals("Quantity must be positive.", result.getMessage());
    }

    @Test
    public void decreaseStock_WhenQuantityIsValid_ShouldDecreaseStock() {
        product.decreaseStock(5);

        assertEquals(5L, product.getQuantity());
    }

    @Test
    public void decreaseStock_WhenQuantityIsZero_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> product.decreaseStock(0));

        assertEquals("Quantity must be positive.", result.getMessage());
    }

    @Test
    public void decreaseStock_WhenQuantityIsGreaterThanStock_ShouldThrowIllegalArgumentException() {
        InsufficientStockException result = assertThrows(InsufficientStockException.class,
                () -> product.decreaseStock(100));

        assertEquals("Insufficient stock.", result.getMessage());
    }

    @Test
    public void toBuilder_ShouldCloneProduct() {
        Product clone = product.toBuilder().build();

        assertEquals(product.getSku(), clone.getSku());
        assertEquals(product.getName(), clone.getName());
        assertEquals(product.getDescription(), clone.getDescription());
        assertEquals(product.getPrice(), clone.getPrice());
        assertEquals(product.getCurrency(), clone.getCurrency());
        assertEquals(product.getActive(), clone.getActive());
        assertEquals(product.getQuantity(), clone.getQuantity());
    }

    @ParameterizedTest
    @ValueSource(ints = { 5, 10 })
    public void hasEnoughStock_WhenQuantityIsLessOrEqualThanStock_ShouldReturnTrue(int quantity) {
        Product product = createProduct();

        boolean result = product.hasEnoughStock(quantity);

        assertTrue(result);
    }

    @Test
    public void hasEnoughStock_WhenQuantityIsGreaterThanStock_ShouldThrowInvalidProductException() {
        Product product = createProduct();

        InvalidProductException result = assertThrows(InvalidProductException.class, () -> product.hasEnoughStock(15));

        assertEquals("Product with SKU SKU-001 does not have enough stock.", result.getMessage());
    }

    private static Product createProduct() {
        return Product.builder()
                .sku("SKU-001")
                .name("Keyboard")
                .description("Mechanical keyboard")
                .price(new BigDecimal("100.00"))
                .currency(Currency.MXN)
                .quantity(10L)
                .build();
    }
}
