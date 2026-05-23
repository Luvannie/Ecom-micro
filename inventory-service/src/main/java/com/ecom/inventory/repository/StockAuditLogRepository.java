package com.ecom.inventory.repository;

import com.ecom.inventory.domain.StockAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockAuditLogRepository extends JpaRepository<StockAuditLog, UUID> {
    List<StockAuditLog> findByProductIdOrderByCreatedAtDesc(UUID productId);
}
