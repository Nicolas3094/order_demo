package com.orders.messages.orders_demo.app.order_item.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order_item.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order_item.api.OrderItemResponse;
import com.orders.messages.orders_demo.app.order_item.internal.exceptions.InvalidOrderItemStateException;
import com.orders.messages.orders_demo.app.order_item.internal.exceptions.OrderItemNotFoundException;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

import jakarta.transaction.Transactional;

@Service
public class OrderItemService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderItemService(
            OrderItemRepository orderItemRepository,
            OrderRepository orderRepository,
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
    public List<OrderItemResponse> getAllOrderItems(UUID orderId) {
        return findOrder(orderId).getItems().stream()
                .map(OrderItemMapper::toResponse)
                .toList();
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
    public OrderItemResponse getOrderItem(UUID orderId, UUID orderItemId) {
        return OrderItemMapper.toResponse(findOrderItem(orderId, orderItemId));
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
    public OrderItemResponse createOrderItem(UUID orderId, CreateOrderItemRequest request) {
        OrderEntity order = findOrder(orderId);

        validatePendingOrder(order);

        ProductEntity product = findProduct(request.sku());

        validateProductIsActive(product);

        order.validateCurrency(product);

        product.decreaseStock(request.quantity());

        OrderItemEntity item = OrderItemMapper.toEntity(request.quantity(), product);

        order.addItem(item);

        productRepository.save(product);

        orderRepository.save(order);

        return OrderItemMapper.toResponse(item);
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
    public OrderItemResponse changeUnitPrice(UUID orderId, UUID orderItemId, BigDecimal unitPrice) {
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
    public OrderItemResponse changeQuantity(UUID orderId, UUID orderItemId, Long quantity) {
        return updateOrderItemState(orderId, orderItemId, orderItem -> {
            ProductEntity product = findProduct(orderItem.getSku());

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
        OrderItemEntity orderItem = findOrderItem(orderId, orderItemId);

        OrderEntity order = orderItem.getOrder();

        validatePendingOrder(order);

        ProductEntity product = findProduct(orderItem.getSku());

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
    private OrderItemResponse updateOrderItemState(UUID orderId, UUID orderItemId, Consumer<OrderItemEntity> action) {
        OrderItemEntity orderItem = findOrderItem(orderId, orderItemId);

        action.accept(orderItem);

        return OrderItemMapper.toResponse(orderItemRepository.save(orderItem));
    }

    /**
     * Validates if an Order has Pending status.
     * 
     * @param order Thhe order.
     */
    private void validatePendingOrder(OrderEntity order) {
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
    private ProductEntity findProduct(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    /**
     * Validates if a product is active.
     *
     * @param product The product to validate.
     */
    private void validateProductIsActive(ProductEntity product) {
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
    private OrderItemEntity findOrderItem(UUID orderId, UUID orderItemId) {
        OrderItemEntity orderItem = orderItemRepository.findById(orderItemId)
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
    private OrderEntity findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

}
