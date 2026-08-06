package com.aivle.backend.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.admin.AdminAuditResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", insertable = false, updatable = false)
    private User actor;
    @Column(length = 20)
    private String actorRole;
    private Long projectId;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Column(length = 60)
    private String aggregateType;

    private Long aggregateId;

    @Column(length = 100)
    private String requestId;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false, length = 20)
    private String result = AdminAuditResult.SUCCESS.name();

    @Column(length = 100)
    private String errorCode;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 255)
    private String targetLabel;

    @Column(length = 500)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String beforeJson;

    @Column(columnDefinition = "TEXT")
    private String afterJson;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private AuditEvent(
        Long actorUserId,
        Long projectId,
        AuditEventType eventType,
        String aggregateType,
        Long aggregateId,
        String requestId,
        String metadataJson,
        LocalDateTime occurredAt
    ) {
        this.actorUserId = actorUserId;
        this.projectId = projectId;
        this.eventType = eventType.name();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.requestId = requestId;
        this.metadataJson = metadataJson;
        this.occurredAt = occurredAt;
    }

    public static AuditEvent record(
        Long actorUserId,
        Long projectId,
        AuditEventType eventType,
        String aggregateType,
        Long aggregateId,
        String requestId,
        String metadataJson,
        LocalDateTime occurredAt
    ) {
        return new AuditEvent(
            actorUserId,
            projectId,
            eventType,
            aggregateType,
            aggregateId,
            requestId,
            metadataJson,
            occurredAt
        );
    }

    public static AuditEvent adminRecord(
        Long actorUserId,
        String actorRole,
        String action,
        String targetType,
        Long targetId,
        String targetLabel,
        AdminAuditResult result,
        String reason,
        String beforeJson,
        String afterJson,
        String errorCode,
        String requestId,
        String ipAddress,
        String userAgent,
        String metadataJson,
        LocalDateTime occurredAt
    ) {
        AuditEvent event = new AuditEvent();
        event.actorUserId = actorUserId;
        event.actorRole = actorRole;
        event.eventType = action;
        event.aggregateType = targetType;
        event.aggregateId = targetId;
        event.targetLabel = targetLabel;
        event.result = result.name();
        event.reason = reason;
        event.beforeJson = beforeJson;
        event.afterJson = afterJson;
        event.errorCode = errorCode;
        event.requestId = requestId;
        event.ipAddress = ipAddress;
        event.userAgent = userAgent;
        event.metadataJson = metadataJson;
        event.occurredAt = occurredAt;
        return event;
    }
}
