package com.orders.messages.orders_demo.app.order.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;

@Service
public class CustomerOrderService {
    private final OrderRepository orderRepository;
    private final CustomerInternalApi customerInternalApi;

    public CustomerOrderService(OrderRepository orderRepository, CustomerInternalApi customerInternalApi) {
        this.orderRepository = orderRepository;
        this.customerInternalApi = customerInternalApi;
    }

    public Page<OrderResponse> getCustomerOrders(UUID customerId, OrderStatus status, Pageable pageable) {
        Page<OrderEntity> orders;

        if (status == null) {
            orders = orderRepository.findByCustomerId(customerId, pageable);
        } else {
            orders = orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        }

        return orders.map(OrderMapper::toResponse);
    }

    /**
     * Retrieves an order from a customer.
     *
     * @param customerId The customer's ID.
     * 
     * @return a list containing all persisted cuustomer's orders.
     */
    public OrderResponse getOrder(UUID orderId, UUID customerId) {
        UUID persistedCustomerId = customerInternalApi.getCustomer(customerId).id();
        return OrderMapper.toResponse(
                orderRepository.findByIdAndCustomerId(orderId, persistedCustomerId)
                        .orElseThrow(OrderNotFoundException::new));
    }

}
