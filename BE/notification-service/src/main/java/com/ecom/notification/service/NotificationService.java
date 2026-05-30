package com.ecom.notification.service;

import com.ecom.notification.domain.NotificationChannel;
import com.ecom.notification.domain.NotificationLog;
import com.ecom.notification.repository.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationLogRepository repository;
    private final TemplateRenderer templateRenderer;
    private final MockEmailSender emailSender;
    private final MockSmsSender smsSender;

    public NotificationService(NotificationLogRepository repository, TemplateRenderer templateRenderer,
                               MockEmailSender emailSender, MockSmsSender smsSender) {
        this.repository = repository;
        this.templateRenderer = templateRenderer;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
    }

    @Transactional
    public NotificationLog sendEmail(UUID userId, String recipient, String templateName, Map<String, Object> model) {
        NotificationLog log = new NotificationLog(userId, NotificationChannel.EMAIL, templateName, recipient,
                subject(templateName));
        try {
            emailSender.send(recipient, subject(templateName), templateRenderer.render(templateName, model));
            log.markSent();
        } catch (RuntimeException exception) {
            log.markFailed(exception.getMessage());
        }
        return repository.save(log);
    }

    @Transactional
    public NotificationLog sendSms(UUID userId, String recipient, String message) {
        NotificationLog log = new NotificationLog(userId, NotificationChannel.SMS, "sms", recipient, null);
        try {
            smsSender.send(recipient, message);
            log.markSent();
        } catch (RuntimeException exception) {
            log.markFailed(exception.getMessage());
        }
        return repository.save(log);
    }

    private String subject(String templateName) {
        return switch (templateName) {
            case "payment-succeeded" -> "Payment received";
            case "payment-failed" -> "Payment failed";
            case "order-confirmed" -> "Order confirmed";
            case "order-cancelled" -> "Order cancelled";
            default -> "Notification";
        };
    }
}
