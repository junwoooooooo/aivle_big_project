package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TaskSchemaVersionContractTests {
    private static final Pattern HASH_VERSION = Pattern.compile(
        "(?:inputHasher|canonicalInputHasher)\\.hash\\([^;]*?\\\"([0-9]+\\.[0-9]+)\\\"",
        Pattern.DOTALL
    );

    @Test
    void everyTaskRunStoresTheOnlySchemaVersionAcceptedByInternalAi() {
        for (TaskType type : TaskType.values()) {
            TaskRun run = TaskRun.create(null, type, "SUBJECT", "subject-1", "{}",
                "sha256:" + "a".repeat(64), "key", "correlation", 1);
            assertThat(run.getContractVersion()).isEqualTo("1.0");
            assertThat(run.getTaskSchemaVersion()).isEqualTo("1.0");
            assertThat(run.getLocale()).isEqualTo("ko-KR");
        }
    }

    @Test
    void everyBackendCanonicalHasherCallUsesTaskSchemaVersionOne() throws Exception {
        List<String> versions = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                var matcher = HASH_VERSION.matcher(Files.readString(path));
                while (matcher.find()) versions.add(matcher.group(1));
            }
        }
        assertThat(versions).hasSizeGreaterThanOrEqualTo(9).containsOnly("1.0");
    }
}
