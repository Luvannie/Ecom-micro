package com.ecom.payment.service;

import com.ecom.payment.domain.Payment;
import com.ecom.payment.domain.PaymentStatus;
import com.ecom.payment.domain.Refund;
import com.ecom.payment.messaging.event.PaymentFailedEvent;
import com.ecom.payment.messaging.event.PaymentRefundedEvent;
import com.ecom.payment.messaging.event.PaymentSucceededEvent;
import com.ecom.payment.outbox.OutboxService;
import com.ecom.payment.provider.PaymentProvider;
import com.ecom.payment.repository.PaymentRepository;
import com.ecom.payment.repository.RefundRepository;
import com.ecom.payment.web.dto.CreatePaymentRequest;
import com.ecom.payment.web.dto.PaymentResponse;
import com.ecom.payment.web.dto.RefundRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentProvider paymentProvider;
    private final OutboxService outboxService;

    public PaymentService(PaymentRepository paymentRepository, RefundRepository refundRepository,
                          PaymentProvider paymentProvider, OutboxService outboxService) {
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
            outboxService.append("Payment", payment.getId(), "payment.succeeded",
                    PaymentSucceededEvent.of(payment.getId(), payment.getOrderId(), payment.getUserId(),
                            payment.getAmount(), payment.getCurrency()));
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
            outboxService.append("Payment", payment.getId(), "payment.failed",
                    PaymentFailedEvent.of(payment.getId(), payment.getOrderId(), payment.getUserId(),
                            payment.getAmount(), payment.getCurrency(), reason));
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
    public PaymentResponse refund(UUID userId, UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidPaymentStateException("Only succeeded payments can be refunded");
        }
        BigDecimal refundAmount = request.amount();
        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new InvalidPaymentStateException("Refund amount cannot exceed payment amount");
        }
        refundRepository.save(new Refund(payment, refundAmount, request.reason()));
        payment.markRefunded();
        outboxService.append("Payment", payment.getId(), "payment.refunded",
                PaymentRefundedEvent.of(payment.getId(), payment.getOrderId(), payment.getUserId(),
                        refundAmount, request.reason()));
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
}
