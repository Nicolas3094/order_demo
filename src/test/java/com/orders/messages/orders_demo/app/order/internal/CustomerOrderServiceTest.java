package com.orders.messages.orders_demo.app.order.internal;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;

import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class CustomerOrderServiceTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerInternalApi customerInternalApi;
    @InjectMocks
    private CustomerOrderService customerOrderService;

    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 15, Sort.by("createdAt").descending());

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class)
    public void shouldGetCustomerOrdersPageWithStatus(OrderStatus orderStatus) {
        UUID customerId = UUID.randomUUID();
        UUID orderId_1 = UUID.randomUUID();
        UUID orderId_2 = UUID.randomUUID();
        Page<OrderEntity> orderPage = new PageImpl<>(
                Arrays.asList(createOrderResponse(orderId_1, customerId), createOrderResponse(orderId_2, customerId)));
        when(orderRepository.findByCustomerIdAndStatus(customerId, orderStatus, DEFAULT_PAGEABLE))
                .thenReturn(orderPage);

        Page<OrderResponse> result = customerOrderService.getCustomerOrders(customerId, orderStatus, DEFAULT_PAGEABLE);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrderResponse::id).containsExactlyInAnyOrder(orderId_1, orderId_2);
    }

    @Test
    public void shouldGetCustomerAllOrdersPageWithNullStatus() {
        UUID customerId = UUID.randomUUID();
        UUID orderId_1 = UUID.randomUUID();
        UUID orderId_2 = UUID.randomUUID();
        Page<OrderEntity> orderPage = new PageImpl<>(
                Arrays.asList(createOrderResponse(orderId_1, customerId), createOrderResponse(orderId_2, customerId)));
        when(orderRepository.findByCustomerId(customerId, DEFAULT_PAGEABLE))
                .thenReturn(orderPage);

        Page<OrderResponse> result = customerOrderService.getCustomerOrders(customerId, null, DEFAULT_PAGEABLE);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrderResponse::id).containsExactlyInAnyOrder(orderId_1, orderId_2);
    }

    @Test
    public void shouldGetOrder() {
        UUID customerId = UUID.randomUUID();
        UUID orderId_1 = UUID.randomUUID();
        CustomerResponse customerResponse = CustomerResponse.builder()
                .id(customerId)
                .name(UUID.randomUUID().toString())
                .email(UUID.randomUUID() + "@email.com")
                .build();
        Optional<OrderEntity> entity = Optional.of(createOrderResponse(orderId_1, customerId));
        when(customerInternalApi.getCustomer(customerId)).thenReturn(customerResponse);
        when(orderRepository.findByIdAndCustomerId(orderId_1, customerId)).thenReturn(entity);

        OrderResponse result = customerOrderService.getOrder(orderId_1, customerId);

        assertThat(result.id()).isEqualTo(orderId_1);
        assertThat(result.customerId()).isEqualTo(customerId);
    }

    @Test
    public void shouldThrowCustomerNotFoundExceptionWhenCustomerNotFound() {
        UUID customerId = UUID.randomUUID();
        UUID orderId_1 = UUID.randomUUID();
        when(customerInternalApi.getCustomer(customerId)).thenThrow(new CustomerNotFoundException());

        CustomerNotFoundException result = assertThrows(CustomerNotFoundException.class,
                () -> customerOrderService.getOrder(orderId_1, customerId));

        assertThat(result.getMessage()).isEqualTo("Customer could not be found.");
        verify(orderRepository, never()).findByIdAndCustomerId(orderId_1, customerId);
    }

    @Test
    public void shouldThrowOrderNotFoundExceptionWhenOrderNotFound() {
        UUID customerId = UUID.randomUUID();
        UUID orderId_1 = UUID.randomUUID();
        CustomerResponse customerResponse = CustomerResponse.builder()
                .id(customerId)
                .name(UUID.randomUUID().toString())
                .email(UUID.randomUUID() + "@email.com")
                .build();
        when(customerInternalApi.getCustomer(customerId)).thenReturn(customerResponse);
        when(orderRepository.findByIdAndCustomerId(orderId_1, customerId)).thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> customerOrderService.getOrder(orderId_1, customerId));

        assertThat(result.getMessage()).isEqualTo("Order could not be found.");
    }

    private static OrderEntity createOrderResponse(UUID id, UUID customerId) {
        return OrderEntity.builder()
                .id(id)
                .customerId(customerId)
                .build();
    }
}
