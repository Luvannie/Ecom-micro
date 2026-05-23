package com.ecom.inventory.messaging;

import com.ecom.inventory.repository.ProductStockRepository;
import com.ecom.inventory.repository.StockAuditLogRepository;
import com.ecom.inventory.repository.StockReservationRepository;
import com.ecom.inventory.service.InventoryService;
import com.ecom.inventory.web.dto.ReservationItemRequest;
import com.ecom.inventory.web.dto.ReservationRequest;
import com.ecom.inventory.web.dto.ReservationResult;
import com.ecom.inventory.web.dto.SetStockRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class ReservationEventConsumerIT {
    @Autowired
    private ReservationEventConsumer consumer;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductStockRepository productStockRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private StockAuditLogRepository auditLogRepository;

    @MockBean
    private InventoryEventProducer eventProducer;

    @BeforeEach
    void resetState() {
        reservationRepository.deleteAll();
        auditLogRepository.deleteAll();
        productStockRepository.deleteAll();
    }

    @Test
    void reservationRequestPublishesSuccessEvent() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventoryService.setStock(productId, new SetStockRequest(5, "load"));

        consumer.handleReservationRequested(new ReservationRequest(
                orderId, List.of(new ReservationItemRequest(productId, 2))));

        ArgumentCaptor<ReservationResult> captor = ArgumentCaptor.forClass(ReservationResult.class);
        verify(eventProducer).publishReserved(captor.capture());
        verify(eventProducer, never()).publishReservationFailed(org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().status().name()).isEqualTo("RESERVED");
    }

    @Test
    void reservationRequestPublishesFailureEvent() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventoryService.setStock(productId, new SetStockRequest(1, "load"));

        consumer.handleReservationRequested(new ReservationRequest(
                orderId, List.of(new ReservationItemRequest(productId, 2))));

        ArgumentCaptor<ReservationResult> captor = ArgumentCaptor.forClass(ReservationResult.class);
        verify(eventProducer).publishReservationFailed(captor.capture());
        verify(eventProducer, never()).publishReserved(org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().status().name()).isEqualTo("FAILED");
        assertThat(captor.getValue().failureReason()).contains(productId.toString());
    }
}
