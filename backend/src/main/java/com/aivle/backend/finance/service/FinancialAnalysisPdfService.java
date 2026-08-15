package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.dto.FinancialModuleResponse.AnnualProjection;
import com.aivle.backend.finance.dto.FinancialModuleResponse.ChartPoint;
import com.aivle.backend.finance.dto.FinancialModuleResponse.StressScenario;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Creates the final, fixed-layout financial report. DOCX is used only as the input template. */
@Service
public class FinancialAnalysisPdfService {
    private static final Color NAVY = new Color(31, 78, 121);
    private static final Color BLUE = new Color(42, 99, 190);
    private static final Color GREEN = new Color(27, 135, 112);
    private static final Color LIGHT = new Color(238, 244, 249);

    public byte[] create(FinancialModuleResponse result) {
        if (result == null) throw new IllegalArgumentException("재무 분석 결과가 없습니다.");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 42, 42);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            document.open();
            Font title = font(22, Font.BOLD, NAVY); Font heading = font(14, Font.BOLD, NAVY);
            Font body = font(9, Font.NORMAL, new Color(38, 48, 61)); Font small = font(8, Font.NORMAL, new Color(91, 106, 122));
            document.add(new Paragraph("재무 분석 결과 보고서", title));
            document.add(new Paragraph("전문 입력 문서 기반 · 생성일 " + LocalDate.now() + " · 금액 단위 KRW", small));
            document.add(spacer(8));
            String headline = result.report() == null ? "입력 가정에 따른 재무 시뮬레이션 결과입니다." : result.report().headline();
            document.add(callout(headline, body));

            var scenarios = result.calculation() == null || result.calculation().scenarios() == null
                ? List.<com.aivle.backend.finance.dto.FinancialModels.ScenarioResult>of() : result.calculation().scenarios();
            var base = scenarios.stream().filter(value -> "BASE".equals(value.code())).findFirst()
                .orElse(scenarios.isEmpty() ? null : scenarios.get(0));
            section(document, "1. 핵심 결과", heading);
            PdfPTable key = new PdfPTable(new float[] {1, 1, 1, 1}); key.setWidthPercentage(100);
            metric(key, "월 손익분기점", base == null || base.breakEvenMonth() == null ? "분석 기간 내 미도달" : base.breakEvenMonth() + "개월 차", body, small);
            metric(key, "초기 투자금 회수", base == null || base.paybackMonth() == null ? "분석 기간 내 미회수" : base.paybackMonth() + "개월 차", body, small);
            metric(key, "필요 운전자금", base == null ? "-" : money(base.requiredWorkingCapital()), body, small);
            metric(key, "총 영업이익", base == null ? "-" : money(base.totalOperatingProfit()), body, small);
            document.add(key);

            section(document, "2. 3개년 추정 손익계산서", heading);
            document.add(annualTable(result.annualProjections(), body, small));

            section(document, "3. 월별 매출·영업이익·누적 현금흐름 예측", heading);
            document.add(new Paragraph("입력한 가격·고객·비용 가정으로 계산한 월별 예측입니다. 점선은 손익분기점 또는 투자금 회수 시점을 표시합니다.", small));
            document.add(cashFlowChart(writer, result.cashFlowChart(), base == null ? null : base.breakEvenMonth(), base == null ? null : base.paybackMonth(), body));

            section(document, "4. 시나리오 스트레스 테스트", heading);
            document.add(stressTable(result.stressScenarios(), body, small));

            section(document, "5. 불확실성 반복 시뮬레이션(몬테카를로)", heading);
            document.add(new Paragraph("가격·수요·비용을 여러 번 무작위로 바꾸어 손실 가능성과 수익 범위를 확인한 결과입니다. 투자 판단에서는 가운데 값(P50)뿐 아니라 보수적 값(P10)도 함께 확인해야 합니다.", small));
            document.add(new Paragraph("몬테카를로 시뮬레이션이란? 하나의 낙관적 숫자만 보는 대신, 가격·고객 수·비용이 달라지는 수많은 경우를 반복 계산해 ‘손실 가능성’과 ‘예상 범위’를 확인하는 방법입니다. P10은 보수적인 결과, P50은 중간 결과, P90은 낙관적인 결과를 뜻합니다.", small));
            if (result.monteCarlo() != null) {
                PdfPTable risk = new PdfPTable(new float[] {1, 1, 1, 1}); risk.setWidthPercentage(100);
                metric(risk, "손실 확률", percent(result.monteCarlo().lossProbabilityPercent()), body, small);
                metric(risk, "보수적 수익 P10", money(result.monteCarlo().profitP10()), body, small);
                metric(risk, "기준 수익 P50", money(result.monteCarlo().profitP50()), body, small);
                metric(risk, "상향 수익 P90", money(result.monteCarlo().profitP90()), body, small);
                document.add(risk);
            }

            section(document, "6. 분석 해석과 권장 조치", heading);
            addList(document, "핵심 발견", result.report() == null ? List.of() : result.report().findings(), body);
            addList(document, "주의 사항", result.report() == null ? List.of() : result.report().cautions(), body);
            addList(document, "권장 조치", result.report() == null ? List.of() : result.report().recommendedActions(), body);
            section(document, "7. 사업 적용 결론", heading);
            document.add(callout(businessConclusion(base), body));
            document.add(spacer(8));
            document.add(new Paragraph("면책: 본 결과는 업로드한 가정에 따른 계획 시뮬레이션이며 실제 성과를 보장하지 않습니다.", small));
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("재무 분석 PDF를 만들 수 없습니다.", exception);
        }
    }

    private String businessConclusion(com.aivle.backend.finance.dto.FinancialModels.ScenarioResult base) {
        if (base == null) return "결론을 만들 수 있는 기준 시나리오 데이터가 없습니다.";
        String profit = money(base.totalOperatingProfit());
        String revenue = money(base.totalRevenue());
        if (base.totalOperatingProfit().signum() >= 0) {
            return "결론: 입력한 가격·고객·비용 가정에서는 분석 기간 동안 총 매출 " + revenue + ", 총 영업이익 " + profit + "이 예상되어 사업 지속 가능성을 확인할 수 있습니다. 다만 실제 판매량과 비용을 지속적으로 검증하고, 보수적 시나리오에서도 필요한 운전자금을 확보하는 것이 필요합니다.";
        }
        return "결론: 입력한 가격·고객·비용 가정에서는 분석 기간 동안 총 매출 " + revenue + "이 예상되지만 총 영업이익 " + profit + "으로 손실이 지속될 가능성이 있습니다. 가격·판매량·변동비 가정을 실제 데이터로 재검증하고, 보수적 시나리오 기준의 현금 계획을 마련한 뒤 사업 확장 여부를 판단해야 합니다.";
    }

    private PdfPTable annualTable(List<AnnualProjection> rows, Font body, Font small) {
        PdfPTable table = new PdfPTable(new float[] {1.1f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f}); table.setWidthPercentage(100);
        for (String value : List.of("연도", "매출", "매출원가", "매출총이익", "판관비", "영업이익")) table.addCell(header(value, small));
        if (rows != null) for (AnnualProjection row : rows) {
            table.addCell(cell(row.year() + "년 차", body)); table.addCell(cell(money(row.revenue()), body));
            table.addCell(cell(money(row.variableCost()), body)); table.addCell(cell(money(row.grossProfit()), body));
            table.addCell(cell(money(row.sellingGeneralAdministrative()), body)); table.addCell(cell(money(row.operatingProfit()), body));
        }
        return table;
    }

    private PdfPTable stressTable(List<StressScenario> rows, Font body, Font small) {
        PdfPTable table = new PdfPTable(new float[] {1.2f, 1.4f, 2, 2}); table.setWidthPercentage(100);
        for (String value : List.of("시나리오", "손익분기점", "총 영업이익", "필요 운전자금")) table.addCell(header(value, small));
        if (rows != null) for (StressScenario row : rows) {
            table.addCell(cell(scenario(row.code()), body));
            table.addCell(cell(row.breakEvenMonth() == null ? "기간 내 미도달" : row.breakEvenMonth() + "개월 차", body));
            table.addCell(cell(money(row.totalOperatingProfit()), body)); table.addCell(cell(money(row.requiredWorkingCapital()), body));
        }
        return table;
    }

    private PdfPTable cashFlowChart(PdfWriter writer, List<ChartPoint> rows, Integer bep, Integer payback, Font body) {
        PdfPTable wrapper = new PdfPTable(1); wrapper.setWidthPercentage(100);
        if (rows == null || rows.isEmpty()) { wrapper.addCell(cell("예측 데이터가 없습니다.", body)); return wrapper; }
        // Reserve room for the chart header and right-hand cash-flow axis.
        float width = 500, height = 210, left = 66, right = 76, bottom = 28, top = 46;
        PdfTemplate chart = writer.getDirectContent().createTemplate(width, height);
        chart.setColorFill(Color.WHITE); chart.rectangle(0, 0, width, height); chart.fill();
        List<BigDecimal> operatingValues = rows.stream().flatMap(row -> java.util.stream.Stream.of(row.revenue(), row.operatingProfit())).filter(java.util.Objects::nonNull).toList();
        List<BigDecimal> cashValues = rows.stream().map(ChartPoint::cumulativeCashFlow).filter(java.util.Objects::nonNull).toList();
        BigDecimal operatingMin = operatingValues.stream().reduce(BigDecimal.ZERO, BigDecimal::min); BigDecimal operatingMax = operatingValues.stream().reduce(BigDecimal.ZERO, BigDecimal::max);
        BigDecimal cashMin = cashValues.stream().reduce(BigDecimal.ZERO, BigDecimal::min); BigDecimal cashMax = cashValues.stream().reduce(BigDecimal.ZERO, BigDecimal::max);
        if (operatingMin.compareTo(operatingMax) == 0) operatingMax = operatingMin.add(BigDecimal.ONE);
        if (cashMin.compareTo(cashMax) == 0) cashMax = cashMin.add(BigDecimal.ONE);
        drawAxis(chart, width, height, left, right, bottom, top, operatingMin, operatingMax, rows.size(), body);
        drawRightAxis(chart, width, height, bottom, top, cashMin, cashMax, body);
        drawLine(chart, rows, operatingMin, operatingMax, width, height, left, right, bottom, top, BLUE, ChartValue.REVENUE, "월 매출", body);
        drawLine(chart, rows, operatingMin, operatingMax, width, height, left, right, bottom, top, new Color(211, 137, 0), ChartValue.PROFIT, "월 영업이익", body);
        drawLine(chart, rows, cashMin, cashMax, width, height, left, right, bottom, top, GREEN, ChartValue.CASH, "누적 현금흐름 (우측 축)", body);
        marker(chart, bep, rows.size(), width, height, left, right, bottom, top, new Color(123, 78, 173), "손익분기점", body);
        marker(chart, payback, rows.size(), width, height, left, right, bottom, top, new Color(196, 63, 63), "투자금 회수", body);
        PdfPCell image = new PdfPCell(com.lowagie.text.Image.getInstance(chart)); image.setPadding(8); image.setBorderColor(new Color(210, 220, 230)); wrapper.addCell(image);
        PdfPCell legend = new PdfPCell(); legend.setPadding(7); legend.setBorderColor(new Color(210, 220, 230));
        legend.addElement(new Phrase("■ 월 매출   ", new Font(body.getBaseFont(), body.getSize(), Font.NORMAL, BLUE)));
        legend.addElement(new Phrase("■ 월 영업이익   ", new Font(body.getBaseFont(), body.getSize(), Font.NORMAL, new Color(211, 137, 0))));
        legend.addElement(new Phrase("■ 누적 현금흐름   ", new Font(body.getBaseFont(), body.getSize(), Font.NORMAL, GREEN)));
        legend.addElement(new Phrase("┆ 손익분기점   ", new Font(body.getBaseFont(), body.getSize(), Font.NORMAL, new Color(123, 78, 173))));
        legend.addElement(new Phrase("┆ 투자금 회수", new Font(body.getBaseFont(), body.getSize(), Font.NORMAL, new Color(196, 63, 63)))); wrapper.addCell(legend);
        return wrapper;
    }

    private void drawAxis(PdfContentByte chart, float width, float height, float left, float right, float bottom, float top, BigDecimal min, BigDecimal max, int points, Font font) {
        chart.setColorStroke(new Color(196, 207, 218)); chart.setLineWidth(.7f);
        float plotRight = width - right;
        chart.moveTo(left, bottom); chart.lineTo(plotRight, bottom); chart.moveTo(left, bottom); chart.lineTo(left, height - top); chart.stroke();
        chart.beginText(); chart.setFontAndSize(font.getBaseFont(), 7); chart.setColorFill(new Color(82, 101, 125));
        chart.showTextAligned(Element.ALIGN_RIGHT, money(max), left - 5, height - top - 2, 0); chart.showTextAligned(Element.ALIGN_RIGHT, money(min), left - 5, bottom - 2, 0);
        chart.showTextAligned(Element.ALIGN_LEFT, "금액 (KRW)", left, height - 18, 0); chart.showTextAligned(Element.ALIGN_RIGHT, "기간 (개월)", plotRight, bottom - 16, 0);
        for (int month = 12; month <= points; month += 12) {
            float x = left + (month - 1) * (plotRight - left) / Math.max(points - 1, 1);
            chart.showTextAligned(Element.ALIGN_CENTER, month + "", x, bottom - 16, 0);
        }
        if (points % 12 != 0 && points > 0) chart.showTextAligned(Element.ALIGN_RIGHT, points + "", plotRight, bottom - 16, 0);
        chart.endText();
    }

    private void drawRightAxis(PdfContentByte chart, float width, float height, float bottom, float top, BigDecimal min, BigDecimal max, Font font) {
        chart.beginText(); chart.setFontAndSize(font.getBaseFont(), 7); chart.setColorFill(GREEN);
        chart.showTextAligned(Element.ALIGN_RIGHT, money(max), width - 6, height - top - 2, 0); chart.showTextAligned(Element.ALIGN_RIGHT, money(min), width - 6, bottom - 2, 0);
        chart.showTextAligned(Element.ALIGN_RIGHT, "누적 현금 (KRW)", width - 6, height - 18, 0); chart.endText();
    }

    private void drawLine(PdfContentByte chart, List<ChartPoint> rows, BigDecimal min, BigDecimal max, float width, float height,
            float left, float right, float bottom, float top, Color color, ChartValue metric, String label, Font font) {
        chart.setColorStroke(color); chart.setLineWidth(1.8f); BigDecimal range = max.subtract(min);
        for (int index = 0; index < rows.size(); index++) {
            BigDecimal value = switch (metric) { case REVENUE -> rows.get(index).revenue(); case PROFIT -> rows.get(index).operatingProfit(); case CASH -> rows.get(index).cumulativeCashFlow(); };
            if (value == null) value = BigDecimal.ZERO;
            float x = left + index * (width - right - left) / Math.max(rows.size() - 1, 1);
            float y = bottom + value.subtract(min).divide(range, 8, java.math.RoundingMode.HALF_UP).floatValue() * (height - bottom - top);
            if (index == 0) chart.moveTo(x, y); else chart.lineTo(x, y);
        }
        chart.stroke(); chart.beginText(); chart.setFontAndSize(font.getBaseFont(), 7); chart.setColorFill(color);
        chart.showTextAligned(Element.ALIGN_LEFT, label, width - right - 140, height - 16 - (metric.ordinal() * 11), 0); chart.endText();
    }

    private void marker(PdfContentByte chart, Integer month, int points, float width, float height, float left, float right, float bottom, float top, Color color, String label, Font font) {
        if (month == null || month < 1 || month > points) return;
        float x = left + (month - 1) * (width - right - left) / Math.max(points - 1, 1);
        chart.setColorStroke(color); chart.setLineWidth(1.1f); chart.setLineDash(4, 3, 0);
        chart.moveTo(x, bottom); chart.lineTo(x, height - top); chart.stroke(); chart.setLineDash(0);
    }

    private void metric(PdfPTable table, String label, String value, Font body, Font small) {
        PdfPCell cell = new PdfPCell(); cell.setPadding(9); cell.setBackgroundColor(LIGHT); cell.setBorderColor(Color.WHITE);
        cell.addElement(new Paragraph(label, small)); Paragraph metric = new Paragraph(value, body); metric.setSpacingBefore(4); cell.addElement(metric); table.addCell(cell);
    }
    private PdfPCell header(String value, Font font) { Font white = new Font(font.getBaseFont(), font.getSize(), font.getStyle(), Color.WHITE); PdfPCell cell = cell(value, white); cell.setBackgroundColor(NAVY); cell.setHorizontalAlignment(Element.ALIGN_CENTER); return cell; }
    private PdfPCell cell(String value, Font font) { PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : value, font)); cell.setPadding(9); cell.setLeading(15, 0); cell.setBorderColor(new Color(210, 220, 230)); return cell; }
    private PdfPTable callout(String value, Font font) { PdfPTable table = new PdfPTable(1); table.setWidthPercentage(100); PdfPCell cell = cell(value, font); cell.setBackgroundColor(LIGHT); cell.setBorderColor(NAVY); cell.setPadding(10); table.addCell(cell); return table; }
    private void section(Document document, String title, Font font) throws Exception { document.add(spacer(16)); Paragraph paragraph = new Paragraph(title, font); paragraph.setSpacingAfter(10); document.add(paragraph); }
    private Paragraph spacer(float size) { Paragraph value = new Paragraph(" "); value.setLeading(size); return value; }
    private void addList(Document document, String title, List<String> values, Font font) throws Exception { Paragraph label = new Paragraph(title, font); label.setSpacingBefore(5); document.add(label); for (String value : values) document.add(new Paragraph("- " + value, font)); }
    private String money(BigDecimal value) { return value == null ? "-" : NumberFormat.getIntegerInstance(Locale.KOREA).format(value) + " KRW"; }
    private String percent(BigDecimal value) { return value == null ? "-" : value.stripTrailingZeros().toPlainString() + "%"; }
    private String scenario(String code) { return switch (code == null ? "" : code.toUpperCase()) { case "CONSERVATIVE" -> "보수적"; case "OPTIMISTIC" -> "낙관적"; default -> "기준"; }; }
    private enum ChartValue { REVENUE, PROFIT, CASH }

    private Font font(float size, int style, Color color) {
        for (String path : List.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0", "C:/Windows/Fonts/malgun.ttf")) {
            try { BaseFont base = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED); return new Font(base, size, style, color); }
            catch (Exception ignored) { }
        }
        return new Font(Font.HELVETICA, size, style, color);
    }
}
