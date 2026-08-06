package com.aivle.backend.pipeline.module;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PipelineModuleStatus {
    NOT_READY("module.status.not_ready"),
    READY("module.status.ready"),
    QUEUED("module.status.queued"),
    RUNNING("module.status.running"),
    NEEDS_INPUT("module.status.needs_input"),
    COMPLETED("module.status.completed"),
    FAILED("module.status.failed"),
    STALE("module.status.stale"),
    NOT_CONNECTED("module.status.not_connected");

    private final String labelKey;
}
