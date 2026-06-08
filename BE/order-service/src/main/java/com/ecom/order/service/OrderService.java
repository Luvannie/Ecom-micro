package com.ecom.order.service;

import com.ecom.common.web.ServiceUnavailableException;
import com.ecom.order.client.CartClient;
import com.ecom.order.client.InventoryClient;
import com.ecom.order.domain.Order;
import com.ecom.order.domain.OrderStatus;
import com.ecom.order.repository.OrderRepository;
import com.ecom.order.web.dto.OrderResponse;
import com.ecom.order.web.dto.OrderSummaryResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final InventoryClient inventoryClient;

    public OrderService(OrderRepository orderRepository, CartClient cartClient, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.cartClient = cartClient;
        this.inventoryClient = inventoryClient;
    }

    @CircuitBreaker(name = "cartService", fallbackMethod = "createOrderFallback")
    @Retry(name = "cartService")
    @Bulkhead(name = "cartService")
    @Transactional
    public OrderResponse createOrder(UUID userId, String email) {
        var cart = cartClient.getCart(userId, email);
        if (cart.items().isEmpty()) {
            throw new EmptyCartException();
        }
        Order order = new Order(userId);
        cart.items().forEach(item -> order.addItem(item.productId(), item.productName(), item.unitPrice(), item.quantity()));
        Order saved = orderRepository.save(order);
        cartClient.clearCart(userId, email);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId) {
        return OrderResponse.from(findForUser(userId, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(OrderSummaryResponse::from);
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "cancelOrderFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    @Transactional
    public OrderResponse cancelOrder(UUID userId, UUID orderId) {
        Order order = findForUser(userId, orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order cannot be cancelled from status " + order.getStatus());
        }
        if (order.getStatus() == OrderStatus.RESERVED && order.getReservationId() != null) {
            inventoryClient.release(order.getReservationId());
        }
        order.cancel();
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public void markReserved(UUID orderId, UUID reservationId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.PENDING) {
            order.markReserved(reservationId);
        }
    }

    @Transactional
    public void markReservationFailed(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.PENDING) {
            order.markFailed();
        }
    }

    @Transactional
    public OrderResponse markConfirmed(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new InvalidOrderStateException("Order can only be confirmed from RESERVED status, current: " + order.getStatus());
        }
        order.markConfirmed();
        return OrderResponse.from(orderRepository.save(order));
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "cancelAfterPaymentFailureFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    @Transactional
    public OrderResponse cancelAfterPaymentFailure(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.RESERVED && order.getReservationId() != null) {
            inventoryClient.release(order.getReservationId());
        }
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.RESERVED) {
            order.cancel();
        }
        return OrderResponse.from(order);
    }

    private Order findForUser(UUID userId, UUID orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private OrderResponse createOrderFallback(UUID userId, String email, Throwable t) {
        if (t instanceof EmptyCartException || t instanceof InvalidOrderStateException
                || t instanceof OrderNotFoundException) {
            // Preserve domain exceptions so business logic is not masked
            throw rethrowUnchecked(t);
        }
        log.warn("cartService unavailable for createOrder(userId={}): {}", userId, t.getMessage());
        throw new ServiceUnavailableException("cart-service", t);
    }

    private OrderResponse cancelOrderFallback(UUID userId, UUID orderId, Throwable t) {
        if (t instanceof InvalidOrderStateException || t instanceof OrderNotFoundException) {
            throw rethrowUnchecked(t);
        }
        log.warn("inventoryService unavailable for cancelOrder(orderId={}): {}", orderId, t.getMessage());
        throw new ServiceUnavailableException("inventory-service", t);
    }

    private OrderResponse cancelAfterPaymentFailureFallback(UUID orderId, Throwable t) {
        if (t instanceof OrderNotFoundException) {
            throw rethrowUnchecked(t);
        }
        log.warn("inventoryService unavailable for cancelAfterPaymentFailure(orderId={}): {}", orderId, t.getMessage());
        throw new ServiceUnavailableException("inventory-service", t);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E rethrowUnchecked(Throwable t) throws E {
        throw (E) t;
    }
}
