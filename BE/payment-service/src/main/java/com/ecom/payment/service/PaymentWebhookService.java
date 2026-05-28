package com.ecom.payment.service;

import com.ecom.payment.domain.PaymentWebhookEvent;
import com.ecom.payment.repository.PaymentRepository;
import com.ecom.payment.repository.PaymentWebhookEventRepository;
import com.ecom.payment.web.dto.MockPaymentWebhookRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookService {
    private final PaymentWebhookEventRepository repository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public PaymentWebhookService(PaymentWebhookEventRepository repository, PaymentRepository paymentRepository,
                                 PaymentService paymentService) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public void handle(MockPaymentWebhookRequest request) {
        if (repository.existsById(request.providerEventId())) {
            return;
        }
        var payment = paymentRepository.findByProviderPaymentId(request.providerPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(request.providerPaymentId()));
        if ("PAYMENT_SUCCEEDED".equals(request.eventType())) {
            paymentService.markSucceeded(request.providerPaymentId(), request.providerEventId());
        } else if ("PAYMENT_FAILED".equals(request.eventType())) {
            paymentService.markFailed(request.providerPaymentId(), request.reason());
        } else {
            throw new InvalidPaymentStateException("Unknown webhook event type: " + request.eventType());
        }
        repository.save(new PaymentWebhookEvent(request.providerEventId(), payment.getId(), request.eventType()));
    }
}
