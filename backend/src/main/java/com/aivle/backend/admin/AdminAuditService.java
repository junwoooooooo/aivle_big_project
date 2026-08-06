package com.aivle.backend.admin;

import com.aivle.backend.audit.AuditEventRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {
    private final AuditEventRepository events;
    private final AdminAuditEventFactory factory;
    private final AdminAuditFailureWriter failureWriter;

    public void recordSuccess(
        Long actorUserId,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        Long targetId,
        String targetLabel,
        String reason,
        Map<String, Object> before,
        Map<String, Object> after,
        AdminAuditContext context,
        Map<String, Object> metadata
    ) {
        events.save(factory.create(new AdminAuditRecord(
            actorUserId, action, targetType, targetId, targetLabel, AdminAuditResult.SUCCESS,
            reason, before, after, null, context, metadata
        )));
    }

    public void recordFailureSafely(
        Long actorUserId,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        Long targetId,
        String targetLabel,
        String reason,
        String errorCode,
        AdminAuditContext context,
        Map<String, Object> metadata
    ) {
        try {
            failureWriter.record(new AdminAuditRecord(
                actorUserId, action, targetType, targetId, targetLabel, AdminAuditResult.FAILED,
                reason, Map.of(), Map.of(), errorCode, context, metadata
            ));
        } catch (RuntimeException auditFailure) {
            log.error(
                "Failed to persist admin failure audit, action={}, actorUserId={}, errorCode={}, requestId={}",
                action, actorUserId, errorCode, context == null ? null : context.requestId(), auditFailure
            );
        }
    }
}
