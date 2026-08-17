package com.aivle.backend.pipeline.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessDocumentService;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LaunchReadinessDocumentServiceTests {
    private final LaunchReadinessDocumentService service = new LaunchReadinessDocumentService();

    @Test
    void technologyTemplateRoundTripsOnlyDeclaredFieldKeys() throws Exception {
        byte[] template = service.template(ModuleType.TECHNOLOGY);
        byte[] completed;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getTables().get(0).getRow(2).getCell(0).setText("API와 데이터베이스를 분리한 3계층 구조");
            document.write(output); completed = output.toByteArray();
        }
        var values = service.parse(ModuleType.TECHNOLOGY, new MockMultipartFile("file", "technology.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", completed));
        assertThat(values).hasSize(10);
        assertThat(values.get("systemArchitecture")).contains("3계층 구조");
        assertThat(values).doesNotContainKey("unknown");
    }

    @Test
    void launchTemplateRoundTripsSinglePurposeReleaseFields() throws Exception {
        byte[] template = service.template(ModuleType.LAUNCH);
        byte[] completed;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getTables().get(0).getRow(2).getCell(0).setText("자전거 대여 운영 조직 대상 제한 출시");
            document.write(output); completed = output.toByteArray();
        }
        var values = service.parse(ModuleType.LAUNCH, new MockMultipartFile("file", "launch.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", completed));
        assertThat(values).hasSize(10);
        assertThat(values.get("releaseScope")).contains("제한 출시");
        assertThat(values).containsKeys("launchCriteria", "incidentAndRollback", "openRisks");
        assertThat(values).doesNotContainKeys("systemArchitecture", "annualFixedLaborCost");
    }
}
