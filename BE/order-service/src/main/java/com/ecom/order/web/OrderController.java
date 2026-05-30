package com.ecom.order.web;

import com.ecom.order.messaging.OrderEventProducer;
import com.ecom.order.security.GatewayUserContext;
import com.ecom.order.service.OrderService;
import com.ecom.order.web.dto.OrderResponse;
import com.ecom.order.web.dto.OrderSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
public class OrderController {
    private final OrderService orderService;
    private final OrderEventProducer eventProducer;

    public OrderController(OrderService orderService, OrderEventProducer eventProducer) {
        this.orderService = orderService;
        this.eventProducer = eventProducer;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderResponse> create(HttpServletRequest request) {
        GatewayUserContext context = GatewayUserContext.from(request);
        OrderResponse order = orderService.createOrder(context.userId(), context.email());
        eventProducer.publishReservationRequested(order);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderResponse get(HttpServletRequest request, @PathVariable("orderId") UUID orderId) {
        return orderService.getOrder(GatewayUserContext.from(request).userId(), orderId);
    }

    @GetMapping("/api/orders")
    public Page<OrderSummaryResponse> list(HttpServletRequest request, @PageableDefault(size = 20) Pageable pageable) {
        return orderService.listOrders(GatewayUserContext.from(request).userId(), pageable);
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(HttpServletRequest request, @PathVariable("orderId") UUID orderId) {
        OrderResponse order = orderService.cancelOrder(GatewayUserContext.from(request).userId(), orderId);
        eventProducer.publishOrderCancelled(order);
        return ResponseEntity.ok(order);
    }
}