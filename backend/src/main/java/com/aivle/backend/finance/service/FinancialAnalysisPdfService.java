package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.pipeline.launchreadiness.application.KoreanPdfFonts;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class FinancialAnalysisPdfService {
    private static final Color NAVY = new Color(31, 78, 121);
    private static final Color LIGHT = new Color(238, 244, 249);
    public byte[] create(FinancialModuleResponse result) {
        if (result == null) throw new IllegalArgumentException("재무 분석 결과가 없습니다.");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 42, 42);
            PdfWriter.getInstance(document, output); document.open();
            Font title = KoreanPdfFonts.font(22, Font.BOLD, NAVY);
            Font heading = KoreanPdfFonts.font(14, Font.BOLD, NAVY);
            Font body = KoreanPdfFonts.font(9, Font.NORMAL, new Color(38, 48, 61));
            Font small = KoreanPdfFonts.font(8, Font.NORMAL, new Color(91, 106, 122));
            document.add(new Paragraph("재무 분석 결과 보고서", title));
            document.add(new Paragraph("사용자 재무 입력 문서 기반 · 생성일 " + LocalDate.now() + " · 금액 단위 KRW", small));
            document.add(spacer());
            if (result.report() != null) document.add(callout(result.report().headline(), body));
            var scenarios = result.calculation() == null ? List.<com.aivle.backend.finance.dto.FinancialModels.ScenarioResult>of() : result.calculation().scenarios();
            var base = scenarios == null ? null : scenarios.stream().filter(value -> "BASE".equals(value.code())).findFirst().orElse(scenarios.isEmpty() ? null : scenarios.get(0));
            section(document, "1. 핵심 결과", heading);
            PdfPTable metrics = new PdfPTable(new float[] {1, 1, 1, 1}); metrics.setWidthPercentage(100);
            metric(metrics, "손익분기점", base == null || base.breakEvenMonth() == null ? "기간 내 미도달" : base.breakEvenMonth() + "개월 차", body, small);
            metric(metrics, "초기 투자금 회수", base == null || base.paybackMonth() == null ? "기간 내 미회수" : base.paybackMonth() + "개월 차", body, small);
            metric(metrics, "필요 운전자금", base == null ? "-" : money(base.requiredWorkingCapital()), body, small);
            metric(metrics, "총 영업이익", base == null ? "-" : money(base.totalOperatingProfit()), body, small);
            document.add(metrics);
            section(document, "2. 3개년 추정 손익", heading);
            PdfPTable annual = new PdfPTable(new float[] {1, 1.5f, 1.5f, 1.5f, 1.5f}); annual.setWidthPercentage(100);
            headers(annual, List.of("연도", "매출", "매출원가", "판관비", "영업이익"), small);
            if (result.annualProjections() != null) for (var row : result.annualProjections()) {
                annual.addCell(cell(row.year() + "년 차", body)); annual.addCell(cell(money(row.revenue()), body));
                annual.addCell(cell(money(row.variableCost()), body)); annual.addCell(cell(money(row.sellingGeneralAdministrative()), body));
                annual.addCell(cell(money(row.operatingProfit()), body));
            }
            document.add(annual);
            section(document, "3. 월별 매출·영업이익·누적 현금흐름", heading);
            PdfPTable monthly = new PdfPTable(new float[] {1, 2, 2, 2}); monthly.setWidthPercentage(100);
            headers(monthly, List.of("개월", "매출", "영업이익", "누적 현금흐름"), small);
            if (result.cashFlowChart() != null) for (var row : result.cashFlowChart()) {
                monthly.addCell(cell(String.valueOf(row.month()), body)); monthly.addCell(cell(money(row.revenue()), body));
                monthly.addCell(cell(money(row.operatingProfit()), body)); monthly.addCell(cell(money(row.cumulativeCashFlow()), body));
            }
            document.add(monthly);
            section(document, "4. 스트레스 테스트", heading);
            PdfPTable stress = new PdfPTable(new float[] {1.4f, 1.4f, 2, 2}); stress.setWidthPercentage(100);
            headers(stress, List.of("시나리오", "손익분기점", "총 영업이익", "필요 운전자금"), small);
            if (result.stressScenarios() != null) for (var row : result.stressScenarios()) {
                stress.addCell(cell(row.label(), body)); stress.addCell(cell(row.breakEvenMonth() == null ? "기간 내 미도달" : row.breakEvenMonth() + "개월 차", body));
                stress.addCell(cell(money(row.totalOperatingProfit()), body)); stress.addCell(cell(money(row.requiredWorkingCapital()), body));
            }
            document.add(stress);
            section(document, "5. Monte Carlo 위험 분포", heading);
            if (result.monteCarlo() != null) document.add(callout("시뮬레이션 " + result.monteCarlo().simulations() + "회 · 손실 확률 "
                + result.monteCarlo().lossProbabilityPercent() + "% · 투자금 회수 확률 " + result.monteCarlo().paybackProbabilityPercent()
                + "% · P10/P50/P90 " + money(result.monteCarlo().profitP10()) + " / " + money(result.monteCarlo().profitP50()) + " / " + money(result.monteCarlo().profitP90()), body));
            section(document, "6. 분석 해석과 권장 조치", heading);
            if (result.report() != null) {
                addList(document, "핵심 발견", result.report().findings(), body);
                addList(document, "주의할 점", result.report().cautions(), body);
                addList(document, "권장 조치", result.report().recommendedActions(), body);
                document.add(new Paragraph(result.report().disclaimer(), small));
            }
            section(document, "7. 사업 적용 결론", heading);
            document.add(callout(base != null && base.totalOperatingProfit().signum() >= 0
                ? "현재 입력 가정에서는 사업 지속 가능성을 확인할 수 있습니다. 실제 판매량과 비용을 정기적으로 비교해 보정하세요."
                : "현재 입력 가정에서는 손실 구조를 먼저 개선해야 합니다. 가격·판매량·변동비와 운전자금을 다시 검증하세요.", body));
            document.close(); return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("재무 분석 PDF를 만들 수 없습니다.", exception); }
    }
    private String money(BigDecimal value) { return value == null ? "-" : NumberFormat.getIntegerInstance(Locale.KOREA).format(value) + " KRW"; }
    private PdfPCell cell(String value, Font font) { PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : value, font)); cell.setPadding(8); cell.setBorderColor(new Color(210, 220, 230)); return cell; }
    private void headers(PdfPTable table, List<String> values, Font font) { Font white = new Font(font.getBaseFont(), font.getSize(), Font.BOLD, Color.WHITE); for (String value : values) { PdfPCell cell = cell(value, white); cell.setBackgroundColor(NAVY); table.addCell(cell); } }
    private void metric(PdfPTable table, String label, String value, Font body, Font small) { PdfPCell cell = new PdfPCell(); cell.setPadding(8); cell.setBackgroundColor(LIGHT); cell.setBorderColor(Color.WHITE); cell.addElement(new Paragraph(label, small)); cell.addElement(new Paragraph(value, body)); table.addCell(cell); }
    private PdfPTable callout(String value, Font font) { PdfPTable table = new PdfPTable(1); table.setWidthPercentage(100); PdfPCell cell = cell(value, font); cell.setBackgroundColor(LIGHT); cell.setBorderColor(NAVY); table.addCell(cell); return table; }
    private void section(Document document, String value, Font font) throws Exception { document.add(spacer()); Paragraph paragraph = new Paragraph(value, font); paragraph.setSpacingAfter(8); document.add(paragraph); }
    private void addList(Document document, String label, List<String> values, Font font) throws Exception { document.add(new Paragraph(label, font)); if (values != null) for (String value : values) document.add(new Paragraph("• " + value, font)); }
    private Paragraph spacer() { Paragraph value = new Paragraph(" "); value.setLeading(14); return value; }
}
