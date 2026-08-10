package com.aivle.backend.pipeline.integration.application;

import com.aivle.backend.pipeline.integration.domain.ModuleType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class HandoffIdempotencyKey {
    private HandoffIdempotencyKey() {}
    public static String create(ModuleType module, String inputSnapshotHash, String requestedOperation) {
        try {
            String value = module.name() + "\n" + inputSnapshotHash + "\n" + requestedOperation;
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
