package com.aivle.backend.admin;

import java.util.Map;

public record AdminAuditRecord(
    Long actorUserId,
    AdminAuditAction action,
    AdminAuditTargetType targetType,
    Long targetId,
    String targetLabel,
    AdminAuditResult result,
    String reason,
    Map<String, Object> before,
    Map<String, Object> after,
    String errorCode,
    AdminAuditContext context,
    Map<String, Object> metadata
) {
    public AdminAuditRecord {
        before = before == null ? Map.of() : Map.copyOf(before);
        after = after == null ? Map.of() : Map.copyOf(after);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
