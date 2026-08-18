package com.aivle.backend.pipeline.finalreport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinalBusinessProposalDocumentServiceTests {
    private final FinalBusinessProposalDocumentService documents =
        new FinalBusinessProposalDocumentService(new ObjectMapper(),
            new com.aivle.backend.pipeline.document.KoreanPdfFontResolver(),
            mock(com.aivle.backend.user.repository.UserRepository.class));

    @Test
    void rendersStructuredProposalAsRealDocxAndPdfDocuments() throws Exception {
        FinalReportSnapshot snapshot = FinalReportSnapshot.create(7L, 1, "{}",
            "sha256:" + "a".repeat(64), proposal(), Instant.parse("2026-08-18T00:00:00Z"), 1L);

        byte[] docx = documents.renderDocx(snapshot);
        byte[] pdf = documents.renderPdf(snapshot);

        assertThat(docx).startsWith(new byte[] {'P', 'K'});
        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(docx))) {
            String text = document.getParagraphs().stream()
                .map(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText)
                .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(text).contains("한국어 · 비용 & 조건 <상한> \"인용\" '단일'", "배달비 3,000원");
        }
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document))
                .contains("사업기획서", "자전거 운영 분석", "한국어 · 비용 & 조건 <상한>", "배달비 3,000원");
        }
    }

    private String proposal() {
        return """
            {"contract":"final-business-proposal-result-v1",
             "cover":{"documentName":"사업기획서","businessName":"자전거 운영 분석","documentStatus":"검토용","approvalPlaceholder":"결재 / 검토"},
             "executiveDecisionSummary":{"businessDefinition":"운영 분석 서비스","purpose":"운영 효율","coreValue":"관리 근거","approvalRequest":"파일럿 승인","targetCustomers":["운영 조직"],"marketEvidence":[],"financialHighlights":[],"keyRisks":[]},
             "sections":[{"number":1,"title":"사업 추진 배경 및 목적","summary":"한국어 · 비용 & 조건 <상한> \\"인용\\" '단일'","narratives":[{"heading":"문제","body":"관리 근거가 필요합니다."}],"keyPoints":["실제 고객 확인"],"tables":[{"title":"실행표","columns":["항목","내용"],"rows":[["파일럿","조건 확인"]]}],"evidenceRefs":["MARKET:market-1"],"evidenceDetails":[{"evidenceKey":"EV-aaaaaaaaaaaaaaaaaaaaaaaa","sourceType":"MARKET","sourceId":"market-1","label":"시장 분석 · 가격·비용 관측","summary":"배달비 3,000원","sourcePath":"시장 분석 · 가격 근거 #3"}]}],
             "appendix":{"assumptions":[],"omittedAnalyses":[],"sourceVersions":["현재 사업안"]}}
            """;
    }
}
