package com.aivle.backend.document.application.processing;

import com.aivle.backend.common.entity.FileStatus;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.document.parsing.*;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.document.structure.StructuredPlanMapper;
import com.aivle.backend.file.storage.FileStorage;
import com.aivle.backend.integration.ai.AiServiceClient;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.aivle.backend.job.runner.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import com.aivle.backend.common.entity.JobType;

@Service
@RequiredArgsConstructor
public class DocumentParseJobExecutor implements DocumentJobExecutor {
    private final DocumentJobContextService contextService;
    private final FileStorage fileStorage;
    private final ObjectStoragePort objectStorage;
    private final DocumentParser documentParser;
    private final DocumentJobProgressService progressService;
    private final DocumentStructureRequestFactory requestFactory;
    private final AiServiceClient aiServiceClient;
    private final StructuredPlanMapper structuredPlanMapper;
    private final DocumentStructureResultHasher resultHasher;
    private final StructuredPlanPersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final ParserArtifactSerializer artifactSerializer;
    private final ParserArtifactObjectService artifactStorage;

    @Override
    public void execute(JobClaim claim) {
        DocumentJobContext context = contextService.load(claim);
        progressService.parsing(claim);
        validateStoredFile(context);
        ParsedDocument parsed = parse(context);
        ParserArtifactPayload payload =
            artifactSerializer.serialize(context, parsed);
        StoredParserArtifact artifact = artifactStorage.store(
            context,
            parsed.parserVersion(),
            payload
        );
        try {
            progressService.parsed(
                claim,
                parsed,
                parseMetadataJson(parsed),
                artifact
            );
        } catch (RuntimeException exception) {
            if (artifact.created()) {
                artifactStorage.deleteBestEffort(
                    artifact.storageKey()
                );
            }
            throw exception;
        }

        var aiRequest = requestFactory.create(context, parsed);
        var aiResponse = callAi(aiRequest);
        progressService.aiResponded(claim, aiResponse.providerRequestId());
        var hashedResult = resultHasher.withCanonicalHash(aiResponse.result());
        var mapping = structuredPlanMapper.map(parsed, hashedResult);
        if (!mapping.mappingErrors().isEmpty()) {
            throw JobProcessingException.nonRetryable(
                "STRUCTURED_RESULT_INVALID",
                "AI 구조화 결과 검증에 실패했습니다.",
                null
            );
        }
        persistenceService.complete(claim, parsed, hashedResult, mapping);
    }

    private void validateStoredFile(DocumentJobContext context) {
        if (context.fileStatus() != FileStatus.AVAILABLE || context.encrypted()) {
            throw JobProcessingException.nonRetryable(
                "STORED_FILE_UNAVAILABLE",
                "저장된 문서를 처리할 수 없습니다.",
                null
            );
        }
    }

    private ParsedDocument parse(DocumentJobContext context) {
        DocumentParseRequest request = new DocumentParseRequest(
            context.originalFileName(),
            context.mimeType(),
            context.sizeBytes(),
            Map.of("checksumSha256", context.checksumSha256())
        );
        if (!documentParser.supports(request)) {
            throw JobProcessingException.nonRetryable(
                "DOCUMENT_FORMAT_UNSUPPORTED",
                "지원하지 않는 문서 형식입니다.",
                null
            );
        }
        try {
            byte[] source = readVerifiedSource(context);
            return documentParser.parse(
                new ByteArrayInputStream(source),
                request
            );
        } catch (DocumentParseException exception) {
            throw JobProcessingException.nonRetryable(
                exception.getErrorCode().name(),
                exception.getMessage(),
                exception
            );
        } catch (IOException exception) {
            throw JobProcessingException.nonRetryable(
                "STORED_FILE_MISSING",
                "저장된 문서를 열 수 없습니다.",
                exception
            );
        }
    }

    private byte[] readVerifiedSource(
        DocumentJobContext context
    ) throws IOException {
        try (InputStream input = openSource(context)) {
            ByteArrayOutputStream output =
                new ByteArrayOutputStream();
            MessageDigest digest = sha256();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > context.sizeBytes()) {
                    throw new IOException(
                        "stored source exceeds expected size"
                    );
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
            String checksum = HexFormat.of().formatHex(
                digest.digest()
            );
            if (total != context.sizeBytes()
                || !checksum.equals(context.checksumSha256())) {
                throw new IOException(
                    "stored source integrity mismatch"
                );
            }
            return output.toByteArray();
        }
    }

    private InputStream openSource(
        DocumentJobContext context
    ) throws IOException {
        if (context.storageType() == StorageType.S3_COMPATIBLE) {
            return objectStorage.open(context.storageKey());
        }
        // LOCAL rows created before D2 live in FileStorage. The local
        // ObjectStorage adapter is also used by tests/development, so a
        // matching object key takes precedence for D2-created rows.
        if (objectStorage.exists(context.storageKey())) {
            return objectStorage.open(context.storageKey());
        }
        return fileStorage.open(context.storageKey());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }

    private com.aivle.backend.integration.ai.document.DocumentStructureAiResponse callAi(
        com.aivle.backend.integration.ai.document.DocumentStructureAiRequest request
    ) {
        try {
            return aiServiceClient.structureDocument(request);
        } catch (AiClientException exception) {
            throw new JobProcessingException(
                exception.getErrorCode(),
                exception.getSafeMessage(),
                exception.isRetryable(),
                exception.getRetryAfter(),
                exception
            );
        }
    }

    private String parseMetadataJson(ParsedDocument parsed) {
        DocumentParseMetadata metadata = new DocumentParseMetadata(
            parsed.parserName(),
            parsed.parserVersion(),
            parsed.totalCharacters(),
            parsed.totalBlocks(),
            parsed.warnings(),
            parsed.parsedAt(),
            parsed.parsingMetadata()
        );
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw JobProcessingException.nonRetryable(
                "PARSE_METADATA_SERIALIZATION_FAILED",
                "문서 파싱 메타데이터를 저장할 수 없습니다.",
                exception
            );
        }
    }

    @Override
    public JobType jobType() {
        return JobType.DOCUMENT_PARSE;
    }
}
