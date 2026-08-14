package com.aivle.backend.pipeline.market.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.pipeline.market.MarketResearchVersion;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskAttempt;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class MarketLedgerArtifactServiceTests {
    private final MarketLedgerArtifactRepository artifacts = mock(MarketLedgerArtifactRepository.class);
    private final TaskRunRepository taskRuns = mock(TaskRunRepository.class);
    private final TaskAttemptRepository attempts = mock(TaskAttemptRepository.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final ObjectKeyGenerator keys = mock(ObjectKeyGenerator.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketLedgerArtifactService service = new MarketLedgerArtifactService(
        artifacts, taskRuns, attempts, storage, keys, mapper);
    private TaskRun task;

    @BeforeEach
    void activeTask() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(41L);
        task = mock(TaskRun.class);
        when(task.getProject()).thenReturn(project);
        when(task.getTaskType()).thenReturn(TaskType.MARKET_RESEARCH);
        when(task.getState()).thenReturn(TaskRunState.RUNNING);
        when(task.getCurrentAttemptId()).thenReturn("attempt-v5");
        when(task.getInputHash()).thenReturn("sha256:" + "b".repeat(64));
        when(task.getInputSnapshot()).thenReturn(input(false));
        when(taskRuns.findById("task-v5")).thenReturn(Optional.of(task));
        when(attempts.findByIdAndTaskRunId("attempt-v5", "task-v5"))
            .thenReturn(Optional.of(mock(TaskAttempt.class)));
    }

    @Test
    void stagesOnlyACompleteChecksummedAllowlistedBundle() throws Exception {
        byte[] bundle = bundle(false);
        when(keys.marketResearchLedger(anyLong(), anyString()))
            .thenAnswer(call -> "projects/41/market-research/ledgers/" + call.getArgument(1) + "/bundle.zip");
        when(storage.store(any(), anyLong(), anyString(), anyString()))
            .thenReturn(new ObjectStoragePort.StoredObject("object.zip", bundle.length,
                MarketLedgerArtifactService.CONTENT_TYPE, sha256(bundle)));
        when(artifacts.save(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.stage("task-v5", "attempt-v5", bundle);

        ArgumentCaptor<MarketLedgerArtifact> captured = ArgumentCaptor.forClass(MarketLedgerArtifact.class);
        verify(artifacts).save(captured.capture());
        assertThat(result.artifactId()).isEqualTo(captured.getValue().getId());
        assertThat(captured.getValue().getState()).isEqualTo(MarketLedgerArtifact.State.STAGED);
        assertThat(captured.getValue().getProjectId()).isEqualTo(41L);
    }

    @Test
    void corruptBundleFailsBeforeObjectStorage() throws Exception {
        assertThatThrownBy(() -> service.stage("task-v5", "attempt-v5", bundle(true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("checksum");
        verifyNoInteractions(storage);
    }

    @Test
    void interruptedUploadNeverCreatesDatabasePointer() throws Exception {
        byte[] bundle = bundle(false);
        when(keys.marketResearchLedger(anyLong(), anyString())).thenReturn("object.zip");
        when(storage.store(any(), anyLong(), anyString(), anyString()))
            .thenThrow(new IOException("interrupted"));

        assertThatThrownBy(() -> service.stage("task-v5", "attempt-v5", bundle))
            .isInstanceOf(IllegalStateException.class);
        verify(artifacts, never()).save(any());
    }

    @Test
    void failedMarketRunDeletesOnlyItsStagedArtifact() throws Exception {
        MarketLedgerArtifact staged = MarketLedgerArtifact.staged(
            "12345678-1234-1234-1234-123456789abc", 41L, "concept-v5", "concept-v5",
            "task-v5", "attempt-v5", "sha256:" + "b".repeat(64),
            "sha256:" + "a".repeat(64), "2026-08-13", "object.zip",
            MarketLedgerArtifactService.CONTENT_TYPE, 3, "c".repeat(64), "d".repeat(64), "{}");
        when(artifacts.findAllByMarketTaskRunIdAndStateAndDeletedAtIsNull(
            "task-v5", MarketLedgerArtifact.State.STAGED)).thenReturn(List.of(staged));

        service.discardStaged("task-v5");

        verify(storage).delete("object.zip");
        verify(artifacts).delete(staged);
    }

    @Test
    void committedArtifactCanBeOpenedTwiceByBoundTaskAndWrongProjectIsRejected() throws Exception {
        String artifactId = "12345678-1234-1234-1234-123456789abc";
        MarketLedgerArtifact artifact = MarketLedgerArtifact.staged(artifactId, 41L, "concept-v5",
            "concept-v5", "old-task", "old-attempt", "sha256:" + "c".repeat(64),
            "sha256:" + "a".repeat(64), "2026-08-13", "object.zip",
            MarketLedgerArtifactService.CONTENT_TYPE, 3, sha256(new byte[]{1, 2, 3}),
            "d".repeat(64), "{}");
        MarketResearchVersion version = mock(MarketResearchVersion.class);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(41L);
        when(version.getProject()).thenReturn(project);
        artifact.commit(version);
        when(task.getInputSnapshot()).thenReturn(input(true));
        when(artifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
        when(storage.exists("object.zip")).thenReturn(true);
        when(storage.metadata("object.zip")).thenReturn(new ObjectStoragePort.ObjectMetadata(
            "object.zip", 3, MarketLedgerArtifactService.CONTENT_TYPE));
        when(storage.open("object.zip")).thenAnswer(call -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(storage.storageType()).thenReturn(StorageType.S3_COMPATIBLE);

        var first = CompletableFuture.supplyAsync(() -> read(service.download(
            "task-v5", "attempt-v5", artifactId)));
        var second = CompletableFuture.supplyAsync(() -> read(service.download(
            "task-v5", "attempt-v5", artifactId)));
        assertThat(first.join()).containsExactly(1, 2, 3);
        assertThat(second.join()).containsExactly(1, 2, 3);
        verify(storage, times(2)).open("object.zip");

        Project foreign = mock(Project.class);
        when(foreign.getId()).thenReturn(99L);
        when(task.getProject()).thenReturn(foreign);
        assertThatThrownBy(() -> service.download("task-v5", "attempt-v5", artifactId))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lineage");
    }

    private String input(boolean restore) {
        ObjectNode root = mapper.createObjectNode();
        root.put("conceptId", "concept-v5"); root.put("asOf", "2026-08-13");
        ObjectNode source = root.putObject("source");
        source.put("projectId", 41); source.put("selectedConceptHash", "sha256:" + "a".repeat(64));
        if (restore) {
            root.put("sourceRun", "concept-v5");
            ObjectNode artifact = root.putObject("ledgerArtifact");
            artifact.put("artifactId", "12345678-1234-1234-1234-123456789abc");
            artifact.put("manifestHash", "d".repeat(64));
            artifact.put("sourceMarketResearchVersionId", 7);
        }
        return root.toString();
    }

    private byte[] bundle(boolean corruptResult) throws Exception {
        byte[] a3 = "{\"a\":1}".getBytes();
        byte[] result = "{\"mode\":\"FULL\"}".getBytes();
        byte[] run = "{\"event\":1}\n".getBytes();
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("artifactContractVersion", MarketLedgerArtifactService.CONTRACT_VERSION);
        manifest.put("projectId", 41); manifest.put("conceptId", "concept-v5");
        manifest.put("conceptSnapshotHash", "sha256:" + "a".repeat(64));
        manifest.put("canonicalInputHash", "sha256:" + "b".repeat(64));
        manifest.put("marketTaskRunId", "task-v5"); manifest.put("taskAttemptId", "attempt-v5");
        manifest.put("asOf", "2026-08-13"); manifest.put("sourceRunId", "concept-v5");
        manifest.putNull("sourceMarketResearchVersionId");
        var files = manifest.putArray("files");
        file(files.addObject(), "a3_bodies.json", a3);
        file(files.addObject(), "result.json", result);
        file(files.addObject(), "run.jsonl", run);
        manifest.put("manifestHash", sha256(mapper.writeValueAsBytes(manifest)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "a3_bodies.json", a3);
            entry(zip, "result.json", corruptResult ? "corrupt".getBytes() : result);
            entry(zip, "run.jsonl", run);
            entry(zip, "manifest.json", mapper.writeValueAsBytes(manifest));
        }
        return bytes.toByteArray();
    }

    private static void file(ObjectNode node, String name, byte[] content) {
        node.put("name", name); node.put("sizeBytes", content.length); node.put("sha256", sha256(content));
    }
    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
    }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static byte[] read(MarketLedgerArtifactService.Download download) {
        try { return download.content().readAllBytes(); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }
}
