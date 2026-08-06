package com.aivle.backend.integration.ai.openai;

import java.math.BigDecimal;
import java.util.List;

final class OpenAiTransportDtos {
    private OpenAiTransportDtos() {
    }

    record ChatRequest(
        String model,
        ResponseFormat response_format,
        List<Message> messages
    ) {
    }

    record ResponseFormat(String type) {
    }

    record Message(String role, String content) {
    }

    record ChatResponse(String id, List<Choice> choices) {
    }

    record Choice(ResponseMessage message) {
    }

    record ResponseMessage(String content) {
    }

    record StructuredResponse(List<StructuredItem> items) {
    }

    record StructuredItem(
        String sectionCode,
        String sectionName,
        String status,
        String extractedContent,
        String reason,
        BigDecimal confidence,
        List<String> evidence,
        List<Integer> sourceBlockReferences
    ) {
    }

    record DocumentInput(List<InputBlock> blocks) {
    }

    record InputBlock(
        int sequence,
        String blockType,
        String text,
        String sourceLocation,
        Integer headingLevel,
        Integer tableRow,
        Integer tableColumn
    ) {
    }

    record CanonicalResult(List<CanonicalItem> items) {
    }

    record CanonicalItem(
        String sectionCode,
        String sectionName,
        String status,
        String extractedContent,
        String reason,
        BigDecimal confidence,
        List<String> evidence,
        List<Integer> sourceBlockReferences
    ) {
    }
}
