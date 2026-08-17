package com.aivle.backend.pipeline.launchreadiness.application;

import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LaunchReadinessDocumentService {
    private static final String BORDER = "C8D4E3";
    public byte[] template(ModuleType type) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            paragraph(document, label(type) + " 전문 입력 템플릿", true, 22, "1F4E79");
            paragraph(document, "각 항목의 값 칸에 기업의 실제 계획을 작성하세요. 이 문서의 입력값이 분석의 1차 근거입니다.", false, 10, "52657D");
            for (Field field : fields(type)) {
                paragraph(document, field.label(), true, 12, "1F4E79");
                paragraph(document, "무엇을 적나요: " + field.guide(), false, 10, "1F2937");
                XWPFTable table = document.createTable(3, 1);
                table.getRow(0).getCell(0).setText("fieldKey: " + field.key());
                table.getRow(1).getCell(0).setText("입력 내용 (아래 칸에 직접 작성)");
                table.getRow(2).getCell(0).setText("\n\n\n");
                style(table);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("입력 템플릿을 만들 수 없습니다.", exception); }
    }

    public Map<String, String> parse(ModuleType type, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("작성한 DOCX 파일을 업로드해 주세요.");
        }
        Map<String, String> values = new LinkedHashMap<>();
        fields(type).forEach(field -> values.put(field.key(), ""));
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            for (XWPFTable table : document.getTables()) {
                if (table.getRows().size() < 3 || table.getRow(0).getTableCells().size() != 1) continue;
                String key = table.getRow(0).getCell(0).getText().trim().replaceFirst("^fieldKey:\\s*", "");
                if (values.containsKey(key)) values.put(key, table.getRow(2).getCell(0).getText().trim());
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("DOCX 템플릿 형식을 읽을 수 없습니다.", exception);
        }
        long entered = values.values().stream().filter(value -> !value.isBlank()).count();
        if (entered == 0) throw new IllegalArgumentException("문서 입력 내용을 확인해 주세요. 작성된 항목이 없습니다.");
        return values;
    }

    public List<Field> fields(ModuleType type) {
        if (type == ModuleType.LAUNCH) return List.of(
            new Field("releaseScope", "출시 범위", "이번 출시에 포함하는 제품·서비스, 대상 고객과 제외 범위"),
            new Field("launchCriteria", "출시 승인 기준", "출시 여부를 결정할 품질·사업·운영 기준과 확인 방법"),
            new Field("goLiveSchedule", "출시 일정과 책임자", "주요 마일스톤, 승인자, 실행 담당자와 의사결정 시점"),
            new Field("customerJourney", "고객 이용 준비", "가입·구매·온보딩·문의 등 출시 직후 고객 경험"),
            new Field("supportPlan", "고객 지원과 공지", "지원 채널, 응답 기준, 안내·변경 공지 계획"),
            new Field("complianceChecklist", "법무·정책·보안 확인", "필수 약관, 개인정보, 권한, 인허가 확인 상태"),
            new Field("monitoringPlan", "출시 모니터링", "출시 후 관찰 지표, 경보 기준, 담당자와 점검 주기"),
            new Field("incidentAndRollback", "장애 대응과 롤백", "중단 기준, 복구·롤백 절차, 고객 커뮤니케이션"),
            new Field("launchCommunications", "출시 커뮤니케이션", "내부 공유, 고객 안내, 채널별 공개 순서"),
            new Field("openRisks", "미해결 위험과 대응", "남은 위험, 영향, 완화 조치, 완료 증빙과 기한"));
        return type == ModuleType.TECHNOLOGY ? List.of(
            new Field("systemArchitecture", "시스템·제품 구조", "구성도, 주요 구성 요소와 연결 관계"),
            new Field("coreFunctions", "핵심 기능과 구현 상태", "기능별 현재 상태와 출시 기준"),
            new Field("techStack", "기술 스택·인프라", "언어, 프레임워크, 클라우드, 데이터베이스"),
            new Field("integrations", "외부 연동·의존성", "API, 결제, 인증, 장애 시 대안"),
            new Field("dataSecurity", "데이터·보안 기준", "개인정보, 권한, 백업, 보안 요구사항"),
            new Field("performanceTarget", "성능·확장 목표", "사용자 수, 응답 시간, 처리량"),
            new Field("developmentTeam", "개발 인력·역할", "담당자, 외주 여부, 책임 범위"),
            new Field("releaseSchedule", "개발·출시 일정", "마일스톤과 완료 기준"),
            new Field("testPlan", "테스트·검증 계획", "테스트 범위, 방법, 통과 기준"),
            new Field("technicalRisks", "기술 위험과 대응", "위험, 영향, 대응책")) : List.of(
            new Field("operatingProcess", "운영 프로세스", "주문·서비스 제공·정산 등 단계별 흐름"),
            new Field("staffing", "인력·역할 체계", "담당자, 근무 체계, 승인 책임"),
            new Field("supplyPartners", "공급·파트너 운영", "공급처, 파트너, 계약·정산 방식"),
            new Field("customerSupport", "고객 지원 체계", "채널, 응답 시간, 문의 처리 기준"),
            new Field("qualityStandards", "품질·SLA 기준", "품질 기준, 서비스 수준, 점검 주기"),
            new Field("incidentResponse", "장애·민원 대응", "발생 시 담당자와 복구·공지 절차"),
            new Field("operatingKpis", "운영 KPI", "처리 시간, 오류율, 만족도 등"),
            new Field("pilotPlan", "파일럿 계획", "대상, 기간, 성공·중단 기준"),
            new Field("scalabilityPlan", "확장 계획", "물량 증가 시 인력·시스템·파트너 대응"),
            new Field("operationalRisks", "운영 위험과 대응", "위험, 영향, 대응책"));
    }

    private void paragraph(XWPFDocument document, String text, boolean bold, int size, String color) {
        XWPFParagraph paragraph = document.createParagraph(); paragraph.setSpacingAfter(55); paragraph.setSpacingBetween(1.15);
        XWPFRun run = paragraph.createRun(); run.setFontFamily("Malgun Gothic"); run.setFontSize(size);
        run.setBold(bold); run.setColor(color); run.setText(text);
    }
    private void style(XWPFTable table) {
        table.setWidth("100%"); table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        for (int row = 0; row < table.getRows().size(); row++) for (XWPFTableCell cell : table.getRow(row).getTableCells()) {
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            cell.setColor(row == 0 ? "EAF2F8" : row == 1 ? "D9E2F3" : "F8FBFF");
            for (XWPFParagraph paragraph : cell.getParagraphs()) for (XWPFRun run : paragraph.getRuns()) {
                run.setFontFamily("Malgun Gothic"); run.setFontSize(10); run.setBold(row == 1);
            }
        }
    }
    private String label(ModuleType type) { return switch (type) {
        case TECHNOLOGY -> "기술"; case OPERATIONS -> "운영"; case LAUNCH -> "출시 준비";
    }; }
    public record Field(String key, String label, String guide) {}
}
