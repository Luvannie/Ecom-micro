package com.ecom.payment.repository;

import com.ecom.payment.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {
}
