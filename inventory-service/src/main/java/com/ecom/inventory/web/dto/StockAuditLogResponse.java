package com.ecom.inventory.web.dto;

import com.ecom.inventory.domain.StockAuditLog;

import java.time.Instant;
import java.util.UUID;

public record StockAuditLogResponse(UUID id, UUID productId, String changeType, int quantityDelta, String reason,
                                    Instant createdAt) {
    public static StockAuditLogResponse from(StockAuditLog log) {
        return new StockAuditLogResponse(log.getId(), log.getProductId(), log.getChangeType(), log.getQuantityDelta(),
                log.getReason(), log.getCreatedAt());
    }
}
