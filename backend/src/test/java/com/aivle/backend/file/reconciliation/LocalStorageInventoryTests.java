package com.aivle.backend.file.reconciliation;

import com.aivle.backend.file.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageInventoryTests {
    @TempDir
    Path root;

    @Test
    void listsOnlyRelativeStorageKeysAndSeparatesQuarantine() throws Exception {
        Files.createDirectories(root.resolve("project"));
        Files.writeString(root.resolve("project/document.bin"), "active");
        Files.createDirectories(root.resolve(".quarantine/project"));
        Files.writeString(
            root.resolve(".quarantine/project/orphan.bin"),
            "quarantined"
        );
        LocalStorageInventory inventory = inventory();

        assertThat(inventory.listActive())
            .extracting(StorageInventory.StorageObject::storageKey)
            .containsExactly("project/document.bin");
        assertThat(inventory.listQuarantined())
            .extracting(StorageInventory.StorageObject::storageKey)
            .containsExactly("project/orphan.bin");
    }

    @Test
    void rejectsStorageKeysThatEscapeConfiguredRoot() {
        LocalStorageInventory inventory = inventory();

        assertThatThrownBy(() -> inventory.quarantine("../outside.bin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("storage key escapes configured root");
    }

    private LocalStorageInventory inventory() {
        return new LocalStorageInventory(new FileStorageProperties(
            root,
            DataSize.ofMegabytes(20),
            DataSize.ofMegabytes(10),
            List.of("docx"),
            List.of("png")
        ));
    }
}
