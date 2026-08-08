package com.ecom.payment.web;

import com.ecom.common.security.GatewayUserContext;
import com.ecom.payment.service.PaymentService;
import com.ecom.payment.web.dto.CreatePaymentRequest;
import com.ecom.payment.web.dto.PaymentResponse;
import com.ecom.payment.web.dto.RefundRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> create(HttpServletRequest request,
                                                  @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                                  @Valid @RequestBody CreatePaymentRequest body) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        PaymentResponse payment = paymentService.createPayment(GatewayUserContext.from(request).userId(), body, key);
        return ResponseEntity.created(URI.create("/api/payments/" + payment.id())).body(payment);
    }

    @GetMapping("/api/payments/{paymentId}")
    public PaymentResponse get(HttpServletRequest request, @PathVariable("paymentId") UUID paymentId) {
        return paymentService.getPayment(GatewayUserContext.from(request).userId(), paymentId);
    }

    @GetMapping("/api/payments")
    public Page<PaymentResponse> list(HttpServletRequest request, @PageableDefault(size = 20) Pageable pageable) {
        return paymentService.listPayments(GatewayUserContext.from(request).userId(), pageable);
    }

    @PostMapping("/api/payments/{paymentId}/refund")
    public PaymentResponse refund(HttpServletRequest request, @PathVariable("paymentId") UUID paymentId, @Valid @RequestBody RefundRequest refundRequest) {
        UUID userId = GatewayUserContext.from(request).userId();
        return paymentService.refund(userId, paymentId, refundRequest);
    }
}
