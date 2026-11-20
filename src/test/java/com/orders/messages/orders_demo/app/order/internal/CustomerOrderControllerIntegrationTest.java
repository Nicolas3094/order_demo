package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;

@AutoConfigureMockMvc
public class CustomerOrderControllerIntegrationTest extends PostgresContainer {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private static final String BASE_PATH = "/api/v1/customers/{customerId}/orders";

    @AfterEach
    public void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
        orderIds.clear();
        customerIds.clear();
    }

    @Test
    public void shouldListCustomerOrdersThroughHttpAndReturn200WithDefaultPaginationAndSorting() throws Exception {
        UUID customerId = persistCustomer();
        List<OrderEntity> orders = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            orders.add(persistOrder(customerId, i % 2 == 0 ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT));
        }
        persistOrder(persistCustomer(), OrderStatus.PAID);
        orders.sort(Comparator.comparing(OrderEntity::getCreatedAt).reversed());

        var result = mockMvc.perform(get(BASE_PATH, customerId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(15))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(15))
                .andExpect(jsonPath("$.totalElements").value(16))
                .andExpect(jsonPath("$.totalPages").value(2));
        for (int i = 0; i < 15; i++) {
            result.andExpect(jsonPath("$.content[" + i + "].id").value(orders.get(i).getId().toString()))
                    .andExpect(jsonPath("$.content[" + i + "].customerId").value(customerId.toString()));
        }
    }

    @Test
    public void shouldBindStatusPageSizeAndSortThroughHttpAndReturnFilteredPage() throws Exception {
        UUID customerId = persistCustomer();
        List<UUID> expectedIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expectedIds.add(persistOrder(customerId, OrderStatus.PAID).getId());
        }
        persistOrder(customerId, OrderStatus.PENDING_PAYMENT);
        persistOrder(persistCustomer(), OrderStatus.PAID);
        expectedIds.sort(Comparator.comparing(UUID::toString));

        mockMvc.perform(get(BASE_PATH, customerId)
                .param("status", "PAID").param("page", "1").param("size", "2").param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value(expectedIds.get(2).toString()))
                .andExpect(jsonPath("$.content[1].id").value(expectedIds.get(3).toString()))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[1].status").value("PAID"));
    }

    @Test
    public void shouldReturn200WithEmptyPageForCustomerWithoutOrders() throws Exception {
        UUID customerId = persistCustomer();
        persistOrder(persistCustomer(), OrderStatus.PAID);

        mockMvc.perform(get(BASE_PATH, customerId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    public void shouldGetCustomerOrderThroughHttpAndReturn200WithJsonBody() throws Exception {
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId, OrderStatus.PAID).getId();

        mockMvc.perform(get(BASE_PATH + "/{orderId}", customerId, orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountTotal").value(247.00))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    public void shouldReturn404WhenListingOrdersForMissingCustomer() throws Exception {
        UUID customerId = UUID.randomUUID();

        mockMvc.perform(get(BASE_PATH, customerId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Customer could not be found."));
    }

    @Test
    public void shouldReturn404WhenGettingOrderForMissingCustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = persistOrder(persistCustomer(), OrderStatus.PAID).getId();

        mockMvc.perform(get(BASE_PATH + "/{orderId}", customerId, orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Customer could not be found."));
    }

    @Test
    public void shouldReturn404WhenGettingMissingOrderForExistingCustomer() throws Exception {
        UUID customerId = persistCustomer();
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get(BASE_PATH + "/{orderId}", customerId, orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order could not be found."));
    }

    @Test
    public void shouldReturn404WhenGettingOrderBelongingToAnotherCustomer() throws Exception {
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(persistCustomer(), OrderStatus.PAID).getId();

        mockMvc.perform(get(BASE_PATH + "/{orderId}", customerId, orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order could not be found."));
    }

    @Test
    public void shouldReturn400ForInvalidStatusParameter() throws Exception {
        UUID customerId = persistCustomer();

        mockMvc.perform(get(BASE_PATH, customerId).param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    public void shouldReturn400ForMalformedCustomerId() throws Exception {
        mockMvc.perform(get(BASE_PATH, "invalid-id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    public void shouldReturn400ForMalformedOrderId() throws Exception {
        UUID customerId = persistCustomer();

        mockMvc.perform(get(BASE_PATH + "/{orderId}", customerId, "invalid-id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    private UUID persistCustomer() {
        return transactionTemplate.execute(status -> {
            UUID id = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer")).getId();
            customerIds.add(id);
            return id;
        });
    }

    private OrderEntity persistOrder(UUID customerId, OrderStatus orderStatus) {
        return transactionTemplate.execute(status -> {
            OrderEntity order = orderRepository.saveAndFlush(OrderEntity.builder()
                    .customerId(customerId)
                    .status(orderStatus)
                    .addItem(OrderItemEntity.builder()
                            .sku("SKU-123")
                            .description("Test item")
                            .unitPrice(new BigDecimal("123.50"))
                            .quantity(2L)
                            .build())
                    .build());
            orderIds.add(order.getId());
            entityManager.clear();
            return orderRepository.findById(order.getId()).orElseThrow();
        });
    }
}
