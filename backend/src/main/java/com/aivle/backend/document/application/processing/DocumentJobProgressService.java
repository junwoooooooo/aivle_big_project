package com.aivle.backend.document.application.processing;

import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentJobProgressService {
    private final AnalysisJobRepository jobRepository;
    private final StoredFileRepository storedFileRepository;
    private final ObjectStoragePort objectStorage;
    private final Clock jobClock;

    @Transactional
    public void parsing(JobClaim claim) {
        AnalysisJob job = requireCurrent(claim);
        job.advance(
            claim.claimToken(),
            claim.attempt(),
            5,
            "PARSING",
            LocalDateTime.now(jobClock)
        );
    }

    @Transactional
    public void parsed(
        JobClaim claim,
        ParsedDocument parsed,
        String metadataJson,
        StoredParserArtifact artifact
    ) {
        AnalysisJob job = requireCurrent(claim);
        var version = job.getSourceDocumentVersion();
        if (version.getParserArtifactStoredFile() == null) {
            StoredFile storedFile = storedFileRepository.save(
                StoredFile.available(
                    objectStorage.storageType(),
                    artifact.storageKey(),
                    "document-blocks.json",
                    artifact.storedFilename(),
                    "json",
                    ParserArtifactObjectService.CONTENT_TYPE,
                    artifact.sizeBytes(),
                    artifact.checksumSha256()
                )
            );
            version.recordParsed(
                parsed.parserName(),
                parsed.parserVersion(),
                metadataJson,
                storedFile,
                artifact.blockCount(),
                artifact.checksumSha256(),
                artifact.schemaVersion(),
                LocalDateTime.now(jobClock)
            );
        } else {
            assertSameArtifact(version, artifact);
        }
        job.advance(
            claim.claimToken(),
            claim.attempt(),
            30,
            "EVALUATING",
            LocalDateTime.now(jobClock)
        );
    }

    private void assertSameArtifact(
        com.aivle.backend.document.entity.DocumentVersion version,
        StoredParserArtifact artifact
    ) {
        if (!version.getParserArtifactStoredFile().getStorageKey()
                .equals(artifact.storageKey())
            || !version.getParserArtifactChecksumSha256()
                .equals(artifact.checksumSha256())
            || !version.getParserBlockCount()
                .equals(artifact.blockCount())) {
            throw new IllegalStateException(
                "parser artifact retry does not match persisted metadata"
            );
        }
    }

    @Transactional
    public void aiResponded(JobClaim claim, String providerRequestId) {
        AnalysisJob job = requireCurrent(claim);
        job.setExternalRequestId(claim.claimToken(), claim.attempt(), providerRequestId);
        job.advance(
            claim.claimToken(),
            claim.attempt(),
            70,
            "PERSISTING",
            LocalDateTime.now(jobClock)
        );
    }

    private AnalysisJob requireCurrent(JobClaim claim) {
        AnalysisJob job = jobRepository.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        return job;
    }
}
