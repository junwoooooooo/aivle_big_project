package com.aivle.backend.pipeline.idea.application;

import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class IdeaBriefAssessmentHasher {
    private final ObjectMapper mapper;

    public IdeaBriefAssessmentHasher(ObjectMapper mapper) { this.mapper = mapper; }

    public String hash(IdeaBrief brief, List<IdeaBriefField> fields) {
        List<Map<String, Object>> canonicalFields = fields.stream()
            .sorted(Comparator.comparing(IdeaBriefField::getFieldKey))
            .map(field -> Map.<String, Object>of(
                "fieldKey", field.getFieldKey(),
                "value", field.getFieldValue() == null ? "" : field.getFieldValue(),
                "decisionState", field.getDecisionState().name(),
                "provenance", field.getProvenance().name()
            )).toList();
        String canonical = mapper.writeValueAsString(Map.of(
            "overview", brief.getOverviewText() == null ? "" : brief.getOverviewText(),
            "fields", canonicalFields,
            "attachmentFileIds", brief.getAttachmentFileIds().stream().sorted().toList()
        ));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
