package com.ecom.inventory.service;

import com.ecom.inventory.domain.ProductStock;
import com.ecom.inventory.domain.ReservationStatus;
import com.ecom.inventory.domain.StockAuditLog;
import com.ecom.inventory.domain.StockReservation;
import com.ecom.inventory.repository.ProductStockRepository;
import com.ecom.inventory.repository.StockAuditLogRepository;
import com.ecom.inventory.repository.StockReservationRepository;
import com.ecom.inventory.web.dto.ReservationItemRequest;
import com.ecom.inventory.web.dto.ReservationRequest;
import com.ecom.inventory.web.dto.ReservationResult;
import com.ecom.inventory.web.dto.SetStockRequest;
import com.ecom.inventory.web.dto.StockAvailability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {
    private final ProductStockRepository productStockRepository;
    private final StockReservationRepository reservationRepository;
    private final StockAuditLogRepository auditLogRepository;

    public InventoryService(ProductStockRepository productStockRepository,
                            StockReservationRepository reservationRepository,
                            StockAuditLogRepository auditLogRepository) {
        this.productStockRepository = productStockRepository;
        this.reservationRepository = reservationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public StockAvailability setStock(UUID productId, SetStockRequest request) {
        ProductStock stock = productStockRepository.findLockedByProductId(productId)
                .orElseGet(() -> new ProductStock(productId, 0));
        int delta = request.quantity() - stock.getAvailableQuantity();
        stock.setAvailableQuantity(request.quantity());
        ProductStock saved = productStockRepository.save(stock);
        auditLogRepository.save(new StockAuditLog(productId, "SET_STOCK", delta, request.reason()));
        return new StockAvailability(saved.getProductId(), saved.getAvailableQuantity(), saved.getReservedQuantity());
    }

    @Transactional(readOnly = true)
    public StockAvailability getAvailability(UUID productId) {
        return productStockRepository.findByProductId(productId)
                .map(stock -> new StockAvailability(stock.getProductId(), stock.getAvailableQuantity(),
                        stock.getReservedQuantity()))
                .orElse(new StockAvailability(productId, 0, 0));
    }

    @Transactional
    public ReservationResult reserve(ReservationRequest request) {
        return reservationRepository.findByOrderId(request.orderId())
                .map(this::toResult)
                .orElseGet(() -> createReservation(request));
    }

    @Transactional
    public void release(UUID reservationId, String reason) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return;
        }
        reservation.getItems().forEach(item -> {
            ProductStock stock = productStockRepository.findLockedByProductId(item.getProductId())
                    .orElseThrow(() -> new StockNotFoundException(item.getProductId()));
            stock.release(item.getQuantity());
            auditLogRepository.save(new StockAuditLog(item.getProductId(), "RELEASE", item.getQuantity(), reason));
        });
        reservation.markReleased();
    }

    @Transactional
    public void commit(UUID reservationId, String reason) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return;
        }
        reservation.getItems().forEach(item -> {
            ProductStock stock = productStockRepository.findLockedByProductId(item.getProductId())
                    .orElseThrow(() -> new StockNotFoundException(item.getProductId()));
            stock.commit(item.getQuantity());
            auditLogRepository.save(new StockAuditLog(item.getProductId(), "COMMIT", -item.getQuantity(), reason));
        });
        reservation.markCommitted();
    }

    @Transactional(readOnly = true)
    public List<StockAuditLog> audit(UUID productId) {
        return auditLogRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    private ReservationResult createReservation(ReservationRequest request) {
        Map<UUID, Integer> quantitiesByProduct = aggregateQuantities(request.items());
        Map<UUID, ProductStock> lockedStocks = new LinkedHashMap<>();
        for (UUID productId : quantitiesByProduct.keySet()) {
            ProductStock stock = productStockRepository.findLockedByProductId(productId)
                    .orElseGet(() -> {
                        ProductStock newStock = new ProductStock(productId, 0);
                        return productStockRepository.save(newStock);
                    });
            lockedStocks.put(productId, stock);
        }

        String failureReason = failureReason(quantitiesByProduct, lockedStocks);
        if (failureReason != null) {
            StockReservation failed = new StockReservation(request.orderId(), ReservationStatus.FAILED);
            quantitiesByProduct.forEach(failed::addItem);
            return toResult(reservationRepository.save(failed), failureReason);
        }

        StockReservation reservation = new StockReservation(request.orderId(), ReservationStatus.RESERVED);
        for (Map.Entry<UUID, Integer> entry : quantitiesByProduct.entrySet()) {
            UUID productId = entry.getKey();
            int quantity = entry.getValue();
            ProductStock stock = lockedStocks.get(productId);
            stock.reserve(quantity);
            productStockRepository.save(stock);
            reservation.addItem(productId, quantity);
            auditLogRepository.save(new StockAuditLog(productId, "RESERVE", -quantity, "order " + request.orderId()));
        }
        return toResult(reservationRepository.save(reservation));
    }

    private Map<UUID, Integer> aggregateQuantities(List<ReservationItemRequest> items) {
        Map<UUID, Integer> quantitiesByProduct = new LinkedHashMap<>();
        items.forEach(item -> quantitiesByProduct.merge(item.productId(), item.quantity(), Integer::sum));
        return quantitiesByProduct;
    }

    private String failureReason(Map<UUID, Integer> quantitiesByProduct, Map<UUID, ProductStock> lockedStocks) {
        for (var entry : quantitiesByProduct.entrySet()) {
            ProductStock stock = lockedStocks.get(entry.getKey());
            if (stock == null || stock.getAvailableQuantity() < entry.getValue()) {
                int available = stock == null ? 0 : stock.getAvailableQuantity();
                return "Insufficient stock for product " + entry.getKey()
                        + ": requested " + entry.getValue() + ", available " + available;
            }
        }
        return null;
    }

    private ReservationResult toResult(StockReservation reservation) {
        return toResult(reservation, null);
    }

    private ReservationResult toResult(StockReservation reservation, String failureReason) {
        return new ReservationResult(reservation.getId(), reservation.getOrderId(), reservation.getStatus(), failureReason);
    }
}
