package com.ecom.payment.service;

import com.ecom.payment.domain.Payment;
import com.ecom.payment.domain.PaymentStatus;
import com.ecom.payment.domain.Refund;
import com.ecom.payment.outbox.OutboxService;
import com.ecom.payment.repository.PaymentRepository;
import com.ecom.payment.repository.RefundRepository;
import com.ecom.payment.web.dto.CreatePaymentRequest;
import com.ecom.payment.web.dto.PaymentResponse;
import com.ecom.payment.web.dto.RefundRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final MockPaymentProvider paymentProvider;
    private final OutboxService outboxService;

    public PaymentService(PaymentRepository paymentRepository, RefundRepository refundRepository,
                          MockPaymentProvider paymentProvider, OutboxService outboxService) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentProvider = paymentProvider;
        this.outboxService = outboxService;
    }

    @Transactional
    public PaymentResponse createPayment(UUID userId, CreatePaymentRequest request, String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> PaymentResponse.from(payment, null))
                .orElseGet(() -> createNewPayment(userId, request, idempotencyKey));
    }

    @Transactional
    public PaymentResponse markSucceeded(String providerPaymentId, String providerEventId) {
        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(providerPaymentId));
        paymentProvider.markSucceeded(providerPaymentId);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            payment.markSucceeded();
            outboxService.append("Payment", payment.getId(), "payment.succeeded", eventPayload(payment));
        }
        return PaymentResponse.from(payment, null);
    }

    @Transactional
    public PaymentResponse markFailed(String providerPaymentId, String reason) {
        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(providerPaymentId));
        paymentProvider.markFailed(providerPaymentId, reason);
        if (payment.getStatus() != PaymentStatus.FAILED) {
            payment.markFailed();
            outboxService.append("Payment", payment.getId(), "payment.failed", eventPayload(payment));
        }
        return PaymentResponse.from(payment, null);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID userId, UUID paymentId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .map(payment -> PaymentResponse.from(payment, null))
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(payment -> PaymentResponse.from(payment, null));
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidPaymentStateException("Only succeeded payments can be refunded");
        }
        refundRepository.save(new Refund(payment, request.amount(), request.reason()));
        payment.markRefunded();
        outboxService.append("Payment", payment.getId(), "payment.refunded", eventPayload(payment));
        return PaymentResponse.from(payment, null);
    }

    private PaymentResponse createNewPayment(UUID userId, CreatePaymentRequest request, String idempotencyKey) {
        paymentRepository.findByOrderId(request.orderId()).ifPresent(payment -> {
            throw new DuplicatePaymentException(request.orderId());
        });
        Payment payment = new Payment(request.orderId(), userId, request.amount(), request.currency(), idempotencyKey);
        var providerPayment = paymentProvider.createPayment(payment.getId(), request.amount(), request.currency());
        payment.attachProviderPayment(providerPayment.providerPaymentId());
        Payment saved = paymentRepository.save(payment);
        return PaymentResponse.from(saved, providerPayment.redirectUrl());
    }

    private Map<String, Object> eventPayload(Payment payment) {
        return Map.of(
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "userId", payment.getUserId(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "status", payment.getStatus().name());
    }
}
