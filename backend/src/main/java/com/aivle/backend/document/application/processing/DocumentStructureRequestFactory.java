package com.aivle.backend.document.application.processing;

import com.aivle.backend.config.AiProperties;
import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.document.structure.BusinessPlanSectionCatalog;
import com.aivle.backend.integration.ai.document.*;
import com.aivle.backend.integration.ai.prompt.DocumentStructurePrompt;
import com.aivle.backend.integration.ai.prompt.DocumentStructurePromptFactory;
import com.aivle.backend.job.runner.JobProcessingException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class DocumentStructureRequestFactory {
    private final AiProperties properties;
    private final BusinessPlanSectionCatalog catalog;
    private final DocumentStructurePromptFactory promptFactory;

    public DocumentStructureRequestFactory(
        AiProperties properties,
        BusinessPlanSectionCatalog catalog,
        DocumentStructurePromptFactory promptFactory
    ) {
        this.properties = properties;
        this.catalog = catalog;
        this.promptFactory = promptFactory;
    }

    public DocumentStructureAiRequest create(
        DocumentJobContext context,
        ParsedDocument parsed
    ) {
        int inputCharacters = parsed.blocks().stream()
            .mapToInt(block -> block.text().length())
            .sum();
        if (inputCharacters > properties.maxInputCharacters()) {
            throw JobProcessingException.nonRetryable(
                "AI_INPUT_LIMIT_EXCEEDED",
                "문서 내용이 AI 구조화 입력 제한을 초과했습니다.",
                null
            );
        }
        DocumentStructurePrompt prompt = promptFactory.current();
        var blocks = parsed.blocks().stream()
            .map(block -> new DocumentStructureBlock(
                block.sequence(),
                block.blockType().name(),
                block.text(),
                block.sourceLocation(),
                block.headingLevel(),
                block.tableRow(),
                block.tableColumn()
            ))
            .toList();
        var sections = catalog.all().stream()
            .map(section -> new DocumentStructureSection(
                section.code().name(),
                section.displayName(),
                section.description(),
                section.required(),
                section.allowedMissingPolicy().name(),
                section.aliases()
            ))
            .toList();
        String requestHash = requestHash(
            context,
            parsed,
            prompt.version(),
            inputCharacters
        );
        return new DocumentStructureAiRequest(
            context.jobId(),
            context.projectId(),
            context.documentVersionId(),
            parsed.parserName(),
            parsed.parserVersion(),
            context.originalFileName(),
            blocks,
            sections,
            prompt.catalogVersion(),
            prompt.version(),
            prompt.template(),
            requestHash
        );
    }

    private String requestHash(
        DocumentJobContext context,
        ParsedDocument parsed,
        String promptVersion,
        int inputCharacters
    ) {
        String value = context.projectId() + "|"
            + context.documentVersionId() + "|"
            + context.checksumSha256() + "|"
            + parsed.parserVersion() + "|"
            + promptVersion + "|"
            + inputCharacters;
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
