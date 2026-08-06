package com.aivle.backend.integration.ai.prompt;

import com.aivle.backend.document.structure.BusinessPlanSectionCatalog;
import com.aivle.backend.document.structure.BusinessPlanSectionDefinition;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class DocumentStructurePromptFactory {
    public static final String PROMPT_VERSION = "business-plan-structure-v1";
    public static final String CATALOG_VERSION = "business-plan-sections-v1";

    private final DocumentStructurePrompt prompt;

    public DocumentStructurePromptFactory(BusinessPlanSectionCatalog catalog) {
        String template = buildTemplate(catalog);
        this.prompt = new DocumentStructurePrompt(
            PROMPT_VERSION,
            CATALOG_VERSION,
            template,
            sha256(template)
        );
    }

    public DocumentStructurePrompt current() {
        return prompt;
    }

    private String buildTemplate(BusinessPlanSectionCatalog catalog) {
        StringBuilder prompt = new StringBuilder("""
            당신은 사업계획서 원문을 12개 canonical section으로 구조화하는 평가 에이전트입니다.
            제공된 block에 실제로 존재하는 내용만 사용하고 원문에 없는 내용을 생성하지 마세요.
            법률·재무 사실을 추측하거나 과장하지 마세요.
            아래 canonical code를 변경하거나 새 code를 만들지 마세요.
            정확히 12개 item을 code 순서대로 하나씩 반환하고 누락 항목도 반드시 반환하세요.
            status는 PRESENT, MISSING, PARTIAL, INVALID 중 하나입니다.
            각 item은 sectionCode, sectionName, status, extractedContent, reason,
            confidence(판단 가능한 경우 0~1), evidence, sourceBlockReferences를 포함합니다.
            sourceBlockReferences는 입력 block의 sequence만 사용하세요.
            JSON object 외의 설명, markdown, code fence를 반환하지 마세요.

            Canonical sections:
            """);
        for (BusinessPlanSectionDefinition definition : catalog.all()) {
            prompt.append(definition.sequence())
                .append(". ")
                .append(definition.code().name())
                .append(" | ")
                .append(definition.displayName())
                .append(" | ")
                .append(definition.description())
                .append(" | required=")
                .append(definition.required())
                .append(" | missingPolicy=")
                .append(definition.allowedMissingPolicy().name())
                .append(" | aliases=")
                .append(String.join(",", definition.aliases()))
                .append('\n');
        }
        prompt.append("""

            Required JSON shape:
            {"items":[{"sectionCode":"<CANONICAL_CODE>","sectionName":"<DISPLAY_NAME>",
            "status":"PRESENT","extractedContent":"<원문 근거 요약>","reason":"",
            "confidence":0.9,"evidence":[],"sourceBlockReferences":[0]}]}
            """);
        return prompt.toString();
    }

    private String sha256(String value) {
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
