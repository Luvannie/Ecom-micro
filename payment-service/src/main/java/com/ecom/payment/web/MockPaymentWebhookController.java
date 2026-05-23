package com.ecom.payment.web;

import com.ecom.payment.service.PaymentWebhookService;
import com.ecom.payment.web.dto.MockPaymentWebhookRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockPaymentWebhookController {
    private final PaymentWebhookService webhookService;

    public MockPaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/api/payments/webhooks/mock")
    public ResponseEntity<Void> handle(@Valid @RequestBody MockPaymentWebhookRequest request) {
        webhookService.handle(request);
        return ResponseEntity.ok().build();
    }
}
