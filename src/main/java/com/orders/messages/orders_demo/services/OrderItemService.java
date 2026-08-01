package com.orders.messages.orders_demo.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreateOrderItemRequest;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.OrderItem;
import com.orders.messages.orders_demo.entity.Product;
import com.orders.messages.orders_demo.exceptions.order_item.InvalidOrderItemStateException;
import com.orders.messages.orders_demo.exceptions.order_item.OrderItemNotFoundException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.product.InvalidProductException;
import com.orders.messages.orders_demo.exceptions.product.ProductNotFoundException;
import com.orders.messages.orders_demo.mappers.OrderItemMapper;
import com.orders.messages.orders_demo.repositories.OrderItemRepository;
import com.orders.messages.orders_demo.repositories.OrderRepository;
import com.orders.messages.orders_demo.repositories.ProductRepository;

import jakarta.transaction.Transactional;

@Service
public class OrderItemService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public OrderItemService(OrderItemRepository orderItemRepository, OrderRepository orderRepository,
            ProductRepository productRepository) {
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * Retrieves all items belonging to the specified order.
     *
     * @param orderId the order identifier.
     * @return a list containing all items associated with the order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    public List<OrderItem> getAllOrderItems(UUID orderId) {
        return findOrder(orderId).getItems();
    }

    /**
     * Retrieves an order item by its identifier and verifies that it belongs to the
     * specified order.
     *
     * @param orderId     the order identifier.
     * @param orderItemId the order item identifier.
     * @return the requested order item.
     * @throws OrderNotFoundException     if the order does not exist.
     * @throws OrderItemNotFoundException if the item does not exist or does not
     *                                    belong to the specified order.
     */
    public OrderItem getOrderItem(UUID orderId, UUID orderItemId) {
        return findOrderItem(orderId, orderItemId);
    }

    /**
     * Creates a new order item and adds it to the specified order.
     *
     * <p>
     * The operation validates that the order is still modifiable, the product is
     * active, both currencies match, and sufficient stock is available. The product
     * stock is decreased before the item is added to the order.
     * </p>
     *
     * @param orderId the order identifier.
     * @param request the order item creation request.
     * @return the newly created order item.
     * @throws OrderNotFoundException         if the order does not exist.
     * @throws ProductNotFoundException       if the product does not exist.
     * @throws InvalidOrderItemStateException if the order can no longer be
     *                                        modified.
     * @throws InvalidProductException        if the product is inactive or cannot
     *                                        be added to the order.
     */
    @Transactional
    public OrderItem createOrderItem(UUID orderId, CreateOrderItemRequest request) {
        Order order = findOrder(orderId);

        validatePendingOrder(order);

        Product product = findProduct(request.sku());

        validateProductIsActive(product);

        order.validateCurrency(product);

        product.decreaseStock(request.quantity());

        OrderItem item = OrderItemMapper.toEntity(request.quantity(), product);

        order.addItem(item);

        productRepository.save(product);

        orderRepository.save(order);

        return item;
    }

    /**
     * Changes the unit price of an existing order item.
     *
     * @param orderId     the order identifier.
     * @param orderItemId the order item identifier.
     * @param unitPrice   the new unit price.
     * @return the updated order item.
     */
    @Transactional
    public OrderItem changeUnitPrice(UUID orderId, UUID orderItemId, BigDecimal unitPrice) {
        return updateOrderItemState(orderId, orderItemId, orderItem -> orderItem.changeUnitPrice(unitPrice));
    }

    /**
     * Changes the quantity of an existing order item and synchronizes the
     * associated
     * product stock.
     *
     * <p>
     * If the requested quantity is greater than the current one, the additional
     * units are deducted from the product stock. If it is lower, the difference is
     * restored to the product stock.
     * </p>
     *
     * @param orderId     the order identifier.
     * @param orderItemId the order item identifier.
     * @param quantity    the new quantity for the order item.
     * @return the updated order item.
     * @throws OrderNotFoundException         if the order does not exist.
     * @throws OrderItemNotFoundException     if the order item doesn't exist or
     *                                        doesn't belong to the specified order.
     * @throws ProductNotFoundException       if the associated product doesn't
     *                                        exist.
     * @throws InvalidOrderItemStateException if the order can no longer be
     *                                        modified.
     * @throws InvalidProductException        if there isn't enough stock available
     *                                        to satisfy the requested quantity.
     */
    @Transactional
    public OrderItem changeQuantity(UUID orderId, UUID orderItemId, Long quantity) {
        return updateOrderItemState(orderId, orderItemId, orderItem -> {
            Product product = findProduct(orderItem.getSku());

            Long initialQuantity = orderItem.getQuantity();

            orderItem.changeQuantity(quantity);

            long delta = quantity - initialQuantity;

            if (delta > 0) {
                product.decreaseStock(delta);

                productRepository.save(product);
            } else if (delta < 0) {
                product.increaseStock(-delta);

                productRepository.save(product);
            }

        });
    }

    @Transactional
    public void deleteOrderItem(UUID orderId, UUID orderItemId) {
        OrderItem orderItem = findOrderItem(orderId, orderItemId);

        Order order = orderItem.getOrder();

        validatePendingOrder(order);

        Product product = findProduct(orderItem.getSku());

        product.increaseStock(orderItem.getQuantity());

        order.removeItem(orderItem);

        productRepository.save(product);

        orderRepository.save(order);
    }

    /**
     * Removes an order item from the specified order and restores the corresponding
     * product stock.
     *
     * @param orderId     the order identifier.
     * @param orderItemId the order item identifier.
     * @throws OrderItemNotFoundException     if the order item does not exist or
     *                                        does
     *                                        not belong to the specified order.
     * @throws ProductNotFoundException       if the associated product does not
     *                                        exist.
     * @throws InvalidOrderItemStateException if the order can no longer be
     *                                        modified.
     */
    private OrderItem updateOrderItemState(UUID orderId, UUID orderItemId, Consumer<OrderItem> action) {
        OrderItem orderItem = findOrderItem(orderId, orderItemId);

        action.accept(orderItem);

        return orderItemRepository.save(orderItem);
    }

    /**
     * Validates if an Order has Pending status.
     * 
     * @param order Thhe order.
     */
    private void validatePendingOrder(Order order) {
        if (!order.canAcceptPayments()) {
            throw new InvalidOrderItemStateException("Only pending orders can modify items.");
        }
    }

    /**
     * Finds the product if it exists, otherwise throws a
     * {@link ProductNotFoundException}.
     *
     * @param sku The product SKU.
     * 
     * @return A complete Product object.
     */
    private Product findProduct(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    /**
     * Validates if a product is active.
     *
     * @param product The product to validate.
     */
    private void validateProductIsActive(Product product) {
        if (!product.getActive()) {
            throw new InvalidProductException(
                    "Product with SKU " + product.getSku() + " is not active.");
        }
    }

    /**
     * Finds the order item if exits, otherwise throws an
     * {@link OrderItemNotFoundException}.
     * 
     * @param orderId     The Order ID.
     * @param orderItemId The Order item ID.
     * @return A complete Order item object.
     */
    private OrderItem findOrderItem(UUID orderId, UUID orderItemId) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(OrderItemNotFoundException::new);

        if (!orderId.equals(orderItem.getOrder().getId())) {
            throw new OrderItemNotFoundException();
        }

        return orderItem;
    }

    /**
     * Finds the Order if exits, otherwise throws an
     * {@link OrderNotFoundException}.
     * 
     * @param orderId The Order ID.
     * @return A complete Order object.
     */
    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

}
