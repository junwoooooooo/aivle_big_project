package com.aivle.backend.taskrun.api;

import java.util.List;

public record ProjectJobHistoryResponse(
    List<ProjectJobView> items,
    int page,
    int size,
    boolean hasMore,
    long totalElements
) { }
