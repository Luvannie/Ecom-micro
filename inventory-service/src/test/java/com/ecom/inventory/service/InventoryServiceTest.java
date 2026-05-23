package com.ecom.inventory.service;

import com.ecom.inventory.domain.ReservationStatus;
import com.ecom.inventory.repository.ProductStockRepository;
import com.ecom.inventory.repository.StockAuditLogRepository;
import com.ecom.inventory.repository.StockReservationRepository;
import com.ecom.inventory.web.dto.ReservationItemRequest;
import com.ecom.inventory.web.dto.ReservationRequest;
import com.ecom.inventory.web.dto.SetStockRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceTest {
    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductStockRepository productStockRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private StockAuditLogRepository auditLogRepository;

    @BeforeEach
    void resetState() {
        reservationRepository.deleteAll();
        auditLogRepository.deleteAll();
        productStockRepository.deleteAll();
    }

    @Test
    void setStockCreatesAvailabilityAndAuditLog() {
        UUID productId = UUID.randomUUID();

        inventoryService.setStock(productId, new SetStockRequest(12, "initial load"));

        var availability = inventoryService.getAvailability(productId);
        assertThat(availability.availableQuantity()).isEqualTo(12);
        assertThat(availability.reservedQuantity()).isZero();
        assertThat(auditLogRepository.findByProductIdOrderByCreatedAtDesc(productId))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getChangeType()).isEqualTo("SET_STOCK");
                    assertThat(log.getQuantityDelta()).isEqualTo(12);
                    assertThat(log.getReason()).isEqualTo("initial load");
                });
    }

    @Test
    void reserveMovesAvailableQuantityToReservedQuantity() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventoryService.setStock(productId, new SetStockRequest(10, "initial load"));

        var result = inventoryService.reserve(new ReservationRequest(orderId, List.of(new ReservationItemRequest(productId, 4))));

        assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
        var availability = inventoryService.getAvailability(productId);
        assertThat(availability.availableQuantity()).isEqualTo(6);
        assertThat(availability.reservedQuantity()).isEqualTo(4);
    }

    @Test
    void reserveFailureLeavesAllStockUnchanged() {
        UUID availableProductId = UUID.randomUUID();
        UUID insufficientProductId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventoryService.setStock(availableProductId, new SetStockRequest(10, "initial load"));
        inventoryService.setStock(insufficientProductId, new SetStockRequest(1, "initial load"));

        var result = inventoryService.reserve(new ReservationRequest(orderId, List.of(
                new ReservationItemRequest(availableProductId, 4),
                new ReservationItemRequest(insufficientProductId, 2))));

        assertThat(result.status()).isEqualTo(ReservationStatus.FAILED);
        assertThat(result.failureReason()).contains(insufficientProductId.toString());
        assertThat(inventoryService.getAvailability(availableProductId).availableQuantity()).isEqualTo(10);
        assertThat(inventoryService.getAvailability(availableProductId).reservedQuantity()).isZero();
        assertThat(inventoryService.getAvailability(insufficientProductId).availableQuantity()).isEqualTo(1);
        assertThat(inventoryService.getAvailability(insufficientProductId).reservedQuantity()).isZero();
    }

    @Test
    void releaseRestoresReservedStockToAvailableStock() {
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, new SetStockRequest(10, "initial load"));
        var result = inventoryService.reserve(new ReservationRequest(
                UUID.randomUUID(), List.of(new ReservationItemRequest(productId, 4))));

        inventoryService.release(result.reservationId(), "customer cancelled");

        var availability = inventoryService.getAvailability(productId);
        assertThat(availability.availableQuantity()).isEqualTo(10);
        assertThat(availability.reservedQuantity()).isZero();
        assertThat(reservationRepository.findById(result.reservationId()))
                .get()
                .extracting("status")
                .isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void commitRemovesReservedStockFromReservationPool() {
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, new SetStockRequest(10, "initial load"));
        var result = inventoryService.reserve(new ReservationRequest(
                UUID.randomUUID(), List.of(new ReservationItemRequest(productId, 4))));

        inventoryService.commit(result.reservationId(), "order paid");

        var availability = inventoryService.getAvailability(productId);
        assertThat(availability.availableQuantity()).isEqualTo(6);
        assertThat(availability.reservedQuantity()).isZero();
        assertThat(reservationRepository.findById(result.reservationId()))
                .get()
                .extracting("status")
                .isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void duplicateReservationForSameOrderReturnsExistingReservation() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventoryService.setStock(productId, new SetStockRequest(10, "initial load"));

        var first = inventoryService.reserve(new ReservationRequest(
                orderId, List.of(new ReservationItemRequest(productId, 4))));
        var second = inventoryService.reserve(new ReservationRequest(
                orderId, List.of(new ReservationItemRequest(productId, 4))));

        assertThat(second.reservationId()).isEqualTo(first.reservationId());
        assertThat(second.status()).isEqualTo(ReservationStatus.RESERVED);
        var availability = inventoryService.getAvailability(productId);
        assertThat(availability.availableQuantity()).isEqualTo(6);
        assertThat(availability.reservedQuantity()).isEqualTo(4);
    }
}
