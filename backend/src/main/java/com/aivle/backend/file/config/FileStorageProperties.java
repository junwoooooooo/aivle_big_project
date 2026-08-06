package com.aivle.backend.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(Path root, DataSize businessPlanMaxSize, DataSize imageMaxSize,
                                    List<String> businessPlanExtensions, List<String> imageExtensions) {}
