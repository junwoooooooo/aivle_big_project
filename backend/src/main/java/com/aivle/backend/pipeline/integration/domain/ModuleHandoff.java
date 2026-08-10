package com.aivle.backend.pipeline.integration.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "module_handoffs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModuleHandoff extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ModuleType module;
    @Column(name = "input_contract", nullable = false, length = 100) private String inputContract;
    @Column(name = "input_snapshot_id", nullable = false, length = 64) private String inputSnapshotId;
    @Column(name = "input_snapshot_hash", nullable = false, length = 71) private String inputSnapshotHash;
    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "TEXT") private String inputSnapshotJson;
    @Column(name = "requested_operation", nullable = false, length = 80) private String requestedOperation;
    @Column(name = "idempotency_key", nullable = false, length = 71) private String idempotencyKey;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "callback_mode", nullable = false, length = 30) private String callbackMode;
    @Column(name = "callback_reference", nullable = false, length = 500) private String callbackReference;
    @Column(name = "requested_by_user_id", nullable = false) private Long requestedByUserId;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;

    public static ModuleHandoff prepare(String id, Long projectId, ModuleType module, String inputContract,
            String snapshotId, String snapshotHash, String inputJson, String operation, String idempotencyKey,
            String callbackReference, Long userId, Instant requestedAt) {
        ModuleHandoff value = new ModuleHandoff();
        value.id = id;
        value.projectId = projectId;
        value.module = module;
        value.inputContract = inputContract;
        value.inputSnapshotId = snapshotId;
        value.inputSnapshotHash = snapshotHash;
        value.inputSnapshotJson = inputJson;
        value.requestedOperation = operation;
        value.idempotencyKey = idempotencyKey;
        value.status = "PREPARED";
        value.callbackMode = "POLL_OR_CALLBACK";
        value.callbackReference = callbackReference;
        value.requestedByUserId = userId;
        value.requestedAt = requestedAt;
        return value;
    }
}
