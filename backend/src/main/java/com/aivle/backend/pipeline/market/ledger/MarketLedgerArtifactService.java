package com.aivle.backend.pipeline.market.ledger;

import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.pipeline.market.MarketResearchVersion;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class MarketLedgerArtifactService {
    private static final Logger log = LoggerFactory.getLogger(MarketLedgerArtifactService.class);
    public static final String CONTENT_TYPE = "application/vnd.aivle.market-ledger+zip";
    public static final String CONTRACT_VERSION = "market-ledger.bundle.v1";
    private static final long MAX_BUNDLE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 24L * 1024 * 1024;
    private static final Set<String> LEDGER_FILES = Set.of("run.jsonl", "a3_bodies.json", "result.json");

    private final MarketLedgerArtifactRepository artifacts;
    private final TaskRunRepository taskRuns;
    private final TaskAttemptRepository attempts;
    private final ObjectStoragePort storage;
    private final ObjectKeyGenerator keys;
    private final ObjectMapper mapper;

    public MarketLedgerArtifactService(MarketLedgerArtifactRepository artifacts,
            TaskRunRepository taskRuns, TaskAttemptRepository attempts,
            ObjectStoragePort storage, ObjectKeyGenerator keys, ObjectMapper mapper) {
        this.artifacts = artifacts;
        this.taskRuns = taskRuns;
        this.attempts = attempts;
        this.storage = storage;
        this.keys = keys;
        this.mapper = mapper;
    }

    @Transactional
    public UploadView stage(String taskRunId, String attemptId, byte[] bundle) {
        if (bundle == null || bundle.length == 0 || bundle.length > MAX_BUNDLE_BYTES) {
            throw new IllegalArgumentException("market ledger bundle size is invalid");
        }
        TaskRun task = activeMarketTask(taskRunId, attemptId);
        JsonNode input = mapper.readTree(task.getInputSnapshot());
        JsonNode manifest = inspectBundle(bundle);
        requireText(manifest, "artifactContractVersion", CONTRACT_VERSION);
        requireText(manifest, "marketTaskRunId", taskRunId);
        requireText(manifest, "taskAttemptId", attemptId);
        requireText(manifest, "canonicalInputHash", task.getInputHash());
        requireText(manifest, "sourceRunId", input.path("conceptId").asText());
        requireText(manifest, "conceptId", input.path("conceptId").asText());
        requireText(manifest, "conceptSnapshotHash", input.path("source").path("selectedConceptHash").asText());
        requireText(manifest, "asOf", input.path("asOf").asText());
        JsonNode expectedSourceVersion = input.path("ledgerArtifact").path("sourceMarketResearchVersionId");
        JsonNode manifestSourceVersion = manifest.path("sourceMarketResearchVersionId");
        if ((expectedSourceVersion.isMissingNode() || expectedSourceVersion.isNull())
                != (manifestSourceVersion.isMissingNode() || manifestSourceVersion.isNull())
                || (!expectedSourceVersion.isMissingNode() && !expectedSourceVersion.isNull()
                    && expectedSourceVersion.longValue() != manifestSourceVersion.longValue())) {
            throw new IllegalArgumentException("market ledger source version binding mismatch");
        }
        if (manifest.path("projectId").longValue() != task.getProject().getId()) {
            throw new IllegalArgumentException("market ledger project binding mismatch");
        }
        String manifestHash = manifest.path("manifestHash").asText();
        if (!manifestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("market ledger manifest hash is invalid");
        }
        String artifactId = UUID.randomUUID().toString();
        String objectKey = keys.marketResearchLedger(task.getProject().getId(), artifactId);
        ObjectStoragePort.StoredObject stored;
        try {
            stored = storage.store(new ByteArrayInputStream(bundle), bundle.length, CONTENT_TYPE, objectKey);
        } catch (IOException failure) {
            throw new IllegalStateException("market ledger artifact upload failed", failure);
        }
        try {
            MarketLedgerArtifact artifact = MarketLedgerArtifact.staged(artifactId,
                task.getProject().getId(), input.path("conceptId").asText(),
                manifest.path("sourceRunId").asText(), taskRunId, attemptId,
                task.getInputHash(), manifest.path("conceptSnapshotHash").asText(),
                manifest.path("asOf").asText(), stored.objectKey(), stored.contentType(),
                stored.sizeBytes(), stored.checksumSha256(), manifestHash, manifest.toString());
            artifacts.save(artifact);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            try { storage.delete(objectKey); }
                            catch (IOException failure) {
                                log.warn("Failed to clean rolled-back Market ledger object {}", objectKey, failure);
                            }
                        }
                    }
                });
            }
            return new UploadView(artifact.getId(), artifact.getManifestHash());
        } catch (RuntimeException failure) {
            try { storage.delete(objectKey); } catch (IOException ignored) { }
            throw failure;
        }
    }

    @Transactional
    public void commit(String taskRunId, String attemptId, MarketResearchVersion version) {
        MarketLedgerArtifact artifact = artifacts
            .findByMarketTaskRunIdAndTaskAttemptIdAndStateAndDeletedAtIsNull(
                taskRunId, attemptId, MarketLedgerArtifact.State.STAGED)
            .orElseThrow(() -> new IllegalStateException("market ledger artifact is missing"));
        if (!artifact.getProjectId().equals(version.getProject().getId())) {
            throw new IllegalStateException("market ledger version project mismatch");
        }
        artifact.commit(version);
        artifacts.save(artifact);
    }

    @Transactional(readOnly = true)
    public SourceView committedForVersion(Long versionId) {
        MarketLedgerArtifact artifact = artifacts
            .findByMarketResearchVersionIdAndStateAndDeletedAtIsNull(
                versionId, MarketLedgerArtifact.State.COMMITTED)
            .orElseThrow(() -> new IllegalArgumentException("market ledger artifact is missing"));
        return new SourceView(artifact.getId(), artifact.getSourceRunId(), artifact.getManifestHash(),
            artifact.getProjectId(), artifact.getConceptId(), artifact.getMarketTaskRunId(),
            artifact.getTaskAttemptId(), artifact.getCanonicalInputHash(),
            artifact.getConceptSnapshotHash(), artifact.getAsOfDate());
    }

    @Transactional
    public void discardStaged(String taskRunId) {
        for (MarketLedgerArtifact artifact : artifacts
                .findAllByMarketTaskRunIdAndStateAndDeletedAtIsNull(
                    taskRunId, MarketLedgerArtifact.State.STAGED)) {
            try {
                storage.delete(artifact.getObjectKey());
                artifacts.delete(artifact);
            } catch (IOException failure) {
                // A staged row is never a recollect authority. Keep it for an operational
                // cleanup retry instead of deleting the only pointer to an orphaned object.
                log.warn("Failed to delete staged Market ledger artifact {}", artifact.getId(), failure);
            }
        }
    }

    @Transactional(readOnly = true)
    public Download download(String requestingTaskRunId, String attemptId, String artifactId) {
        TaskRun task = activeMarketTask(requestingTaskRunId, attemptId);
        JsonNode input = mapper.readTree(task.getInputSnapshot());
        if (!artifactId.equals(input.path("ledgerArtifact").path("artifactId").asText())) {
            throw new IllegalArgumentException("market ledger request binding mismatch");
        }
        MarketLedgerArtifact artifact = artifacts.findById(artifactId)
            .filter(value -> value.getDeletedAt() == null && value.getState() == MarketLedgerArtifact.State.COMMITTED)
            .orElseThrow(() -> new IllegalArgumentException("market ledger artifact is unavailable"));
        if (!artifact.getProjectId().equals(task.getProject().getId())
                || !artifact.getConceptId().equals(input.path("conceptId").asText())
                || !artifact.getSourceRunId().equals(input.path("sourceRun").asText())
                || !artifact.getManifestHash().equals(input.path("ledgerArtifact").path("manifestHash").asText())) {
            throw new IllegalArgumentException("market ledger lineage mismatch");
        }
        try {
            if (!storage.exists(artifact.getObjectKey())) {
                throw new IllegalStateException("market ledger object is missing");
            }
            ObjectStoragePort.ObjectMetadata metadata = storage.metadata(artifact.getObjectKey());
            boolean validContentType = CONTENT_TYPE.equals(metadata.contentType())
                || (storage.storageType() == StorageType.LOCAL
                    && (metadata.contentType() == null
                        || "application/zip".equals(metadata.contentType())
                        || "application/x-zip-compressed".equals(metadata.contentType())));
            if (metadata.sizeBytes() != artifact.getSizeBytes() || !validContentType) {
                throw new IllegalStateException("market ledger object metadata mismatch");
            }
            return new Download(storage.open(artifact.getObjectKey()), artifact.getSizeBytes(),
                artifact.getObjectChecksumSha256(), artifact.getManifestHash());
        } catch (IOException failure) {
            throw new IllegalStateException("market ledger artifact download failed", failure);
        }
    }

    private TaskRun activeMarketTask(String taskRunId, String attemptId) {
        TaskRun task = taskRuns.findById(taskRunId)
            .orElseThrow(() -> new IllegalArgumentException("market task run is missing"));
        if (task.getTaskType() != TaskType.MARKET_RESEARCH
                || task.getState() != TaskRunState.RUNNING
                || !attemptId.equals(task.getCurrentAttemptId())
                || attempts.findByIdAndTaskRunId(attemptId, taskRunId).isEmpty()) {
            throw new IllegalArgumentException("market task attempt is not active");
        }
        return task;
    }

    private JsonNode inspectBundle(byte[] bundle) {
        Set<String> found = new HashSet<>();
        Map<String, byte[]> contentByName = new HashMap<>();
        JsonNode manifest = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || name.contains("/") || name.contains("\\")
                        || !(LEDGER_FILES.contains(name) || "manifest.json".equals(name))
                        || !found.add(name)) {
                    throw new IllegalArgumentException("market ledger bundle contains an invalid path");
                }
                byte[] content = readBounded(zip, MAX_FILE_BYTES);
                if ("manifest.json".equals(name)) manifest = mapper.readTree(content);
                else contentByName.put(name, content);
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("market ledger bundle is invalid", failure);
        }
        if (manifest == null || !found.equals(Set.of("manifest.json", "run.jsonl", "a3_bodies.json", "result.json"))) {
            throw new IllegalArgumentException("market ledger bundle is incomplete");
        }
        JsonNode files = manifest.path("files");
        if (!files.isArray() || files.size() != LEDGER_FILES.size()) {
            throw new IllegalArgumentException("market ledger manifest file list is invalid");
        }
        Set<String> declared = new HashSet<>();
        for (JsonNode file : files) {
            String name = file.path("name").asText();
            byte[] content = contentByName.get(name);
            if (!LEDGER_FILES.contains(name) || !declared.add(name) || content == null
                    || file.path("sizeBytes").longValue() != content.length
                    || !sha256(content).equals(file.path("sha256").asText())) {
                throw new IllegalArgumentException("market ledger file checksum mismatch");
            }
        }
        ObjectNode hashInput = (ObjectNode) manifest.deepCopy();
        String declaredManifestHash = hashInput.remove("manifestHash").asText();
        if (!sha256(mapper.writeValueAsBytes(hashInput)).equals(declaredManifestHash)) {
            throw new IllegalArgumentException("market ledger manifest checksum mismatch");
        }
        return manifest;
    }

    private static byte[] readBounded(InputStream input, long max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > max) throw new IllegalArgumentException("market ledger file is too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void requireText(JsonNode node, String field, String expected) {
        if (expected == null || expected.isBlank() || !expected.equals(node.path(field).asText())) {
            throw new IllegalArgumentException("market ledger " + field + " mismatch");
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    public record UploadView(String artifactId, String manifestHash) { }
    public record SourceView(String artifactId, String sourceRunId, String manifestHash,
                             Long projectId, String conceptId, String marketTaskRunId,
                             String taskAttemptId, String canonicalInputHash,
                             String conceptSnapshotHash, String asOf) { }
    public record Download(InputStream content, long sizeBytes, String checksumSha256,
                           String manifestHash) { }
}
