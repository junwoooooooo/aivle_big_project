package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.dto.FinancialModuleResponse.AnnualProjection;
import com.aivle.backend.finance.dto.FinancialModuleResponse.ChartPoint;
import com.aivle.backend.finance.dto.FinancialModuleResponse.StressScenario;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.stereotype.Service;

/** Produces a portable DOCX containing every persisted financial-analysis result. */
@Service
public class FinancialAnalysisDocumentService {
    private static final String INK = "1F302A";
    private static final String MINT_DARK = "27654F";
    private static final String MINT = "BCEFDC";
    private static final String MINT_LIGHT = "EEF9F5";

    public byte[] create(FinancialModuleResponse result) {
        if (result == null) throw new IllegalArgumentException("재무 분석 결과가 없습니다.");
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            title(document, "재무 분석 결과 보고서");
            subtitle(document, "생성일 " + LocalDate.now() + " · 모든 금액 단위 KRW");
            callout(document, localizedReportText(text(result.report() == null ? null : result.report().headline(), "분석 결과 요약을 확인해 주세요.")));

            heading(document, "1. 핵심 결과");
            var scenarios = result.calculation() == null || result.calculation().scenarios() == null ? List.<com.aivle.backend.finance.dto.FinancialModels.ScenarioResult>of()
                : result.calculation().scenarios();
            var base = scenarios.stream().filter(value -> "BASE".equals(value.code())).findFirst().orElse(scenarios.isEmpty() ? null : scenarios.get(0));
            keyValues(document, List.of(
                new String[] {"기준 시나리오", base == null ? "-" : scenarioLabel(base.code(), base.label())},
                new String[] {"손익분기점", base == null || base.breakEvenMonth() == null ? "분석 기간 내 미도달" : base.breakEvenMonth() + "개월 차"},
                new String[] {"초기 투자금 회수", base == null || base.paybackMonth() == null ? "분석 기간 내 미회수" : base.paybackMonth() + "개월 차"},
                new String[] {"필요 운전자금", base == null ? "-" : money(base.requiredWorkingCapital())},
                new String[] {"총 영업이익", base == null ? "-" : money(base.totalOperatingProfit())}
            ));

            heading(document, "2. 3개년 추정 손익계산서");
            annualTable(document, result.annualProjections());

            heading(document, "3. 월별 매출 및 현금흐름");
            paragraph(document, "그래프 아래에는 6개월 단위와 주요 전환 시점만 요약해 제공합니다. 0보다 작은 영업이익은 해당 월 현금 감소를 의미합니다.");
            chart(document, "월별 매출 예측", result.cashFlowChart(), ChartMetric.REVENUE, new Color(36, 95, 192));
            chart(document, "누적 현금흐름 예측", result.cashFlowChart(), ChartMetric.CUMULATIVE_CASH, new Color(22, 130, 108));
            monthlyMilestoneTable(document, result.cashFlowChart());

            heading(document, "4. 시나리오 스트레스 테스트");
            paragraph(document, "보수적·기준·낙관적 가정에 따른 손익분기점, 총 영업이익, 필요 운전자금을 비교합니다.");
            scenarioTable(document, result.stressScenarios());
            scenarioChart(document, result.stressScenarios());

            heading(document, "5. 몬테카를로 시뮬레이션");
            monteCarloTable(document, result);
            monteCarloRangeChart(document, result.monteCarlo());

            heading(document, "6. AI 분석 요약 및 권고");
            reportSection(document, "핵심 발견", result.report() == null ? List.of() : result.report().findings());
            reportSection(document, "주의 사항", result.report() == null ? List.of() : result.report().cautions());
            reportSection(document, "권장 조치", result.report() == null ? List.of() : result.report().recommendedActions());
            paragraph(document, "면책: " + localizedReportText(text(result.report() == null ? null : result.report().disclaimer(), "본 결과는 입력 가정에 따른 계획 시뮬레이션입니다.")));
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("재무 분석 결과 문서를 만들 수 없습니다.", exception);
        }
    }

    private void annualTable(XWPFDocument document, List<AnnualProjection> rows) {
        XWPFTable table = document.createTable((rows == null ? 0 : rows.size()) + 1, 7);
        String[] headers = {"연도", "매출", "매출원가", "매출총이익", "판관비", "영업이익", "영업이익률"};
        for (int index = 0; index < headers.length; index++) table.getRow(0).getCell(index).setText(headers[index]);
        if (rows != null) for (int row = 0; row < rows.size(); row++) {
            AnnualProjection value = rows.get(row); String[] cells = {(value.year()) + "년 차", money(value.revenue()), money(value.variableCost()),
                money(value.grossProfit()), money(value.sellingGeneralAdministrative()), money(value.operatingProfit()), percent(value.operatingMarginPercent())};
            for (int column = 0; column < cells.length; column++) table.getRow(row + 1).getCell(column).setText(cells[column]);
        }
        styleTable(table);
    }

    private void monthlyMilestoneTable(XWPFDocument document, List<ChartPoint> rows) {
        List<ChartPoint> milestones = milestones(rows);
        XWPFTable table = document.createTable(milestones.size() + 1, 4);
        String[] headers = {"월", "매출", "영업이익", "누적 현금흐름"};
        for (int index = 0; index < headers.length; index++) table.getRow(0).getCell(index).setText(headers[index]);
        for (int row = 0; row < milestones.size(); row++) {
            ChartPoint value = milestones.get(row); String[] cells = {value.month() + "개월", money(value.revenue()), money(value.operatingProfit()), money(value.cumulativeCashFlow())};
            for (int column = 0; column < cells.length; column++) table.getRow(row + 1).getCell(column).setText(cells[column]);
        }
        styleTable(table);
    }

    private List<ChartPoint> milestones(List<ChartPoint> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        Set<Integer> indices = new LinkedHashSet<>();
        indices.add(0);
        for (int index = 5; index < rows.size(); index += 6) indices.add(index);
        for (int index = 0; index < rows.size(); index++) if (rows.get(index).operatingProfit() != null
                && rows.get(index).operatingProfit().signum() >= 0) { indices.add(index); break; }
        indices.add(rows.size() - 1);
        return indices.stream().sorted().map(rows::get).toList();
    }

    private void chart(XWPFDocument document, String label, List<ChartPoint> rows, ChartMetric metric, Color color) throws IOException {
        if (rows == null || rows.isEmpty()) return;
        paragraph(document, label);
        int width = 610, height = 205, left = 58, right = 20, top = 24, bottom = 32;
        List<BigDecimal> values = rows.stream().map(value -> metric == ChartMetric.REVENUE ? value.revenue() : value.cumulativeCashFlow()).toList();
        BigDecimal actualMin = values.stream().reduce(BigDecimal::min).orElse(BigDecimal.ZERO);
        BigDecimal actualMax = values.stream().reduce(BigDecimal::max).orElse(BigDecimal.ZERO);
        BigDecimal min = actualMin.signum() >= 0 ? actualMin.multiply(BigDecimal.valueOf(.9)) : actualMin.multiply(BigDecimal.valueOf(1.1));
        BigDecimal max = actualMax.signum() <= 0 ? actualMax.multiply(BigDecimal.valueOf(.9)) : actualMax.multiply(BigDecimal.valueOf(1.1));
        if (min.compareTo(max) == 0) { min = min.subtract(BigDecimal.ONE); max = max.add(BigDecimal.ONE); }
        BigDecimal range = max.subtract(min);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
        int zeroY = top + max.multiply(BigDecimal.valueOf(height - top - bottom)).divide(range, 6, java.math.RoundingMode.HALF_UP).intValue();
        graphics.setColor(new Color(229, 239, 235)); graphics.drawLine(left, top, width - right, top); if (zeroY >= top && zeroY <= height - bottom) graphics.drawLine(left, zeroY, width - right, zeroY); graphics.drawLine(left, height - bottom, width - right, height - bottom);
        graphics.setColor(new Color(119, 132, 127)); graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(axisNumber(max), 4, top + 4); if (zeroY >= top && zeroY <= height - bottom) graphics.drawString("0", 35, zeroY + 4); graphics.drawString(axisNumber(min), 4, height - bottom + 4); graphics.drawString("M1", left, height - 12); graphics.drawString("M" + rows.size(), width - right - 24, height - 12);
        graphics.setColor(color); graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int previousX = 0, previousY = 0;
        for (int index = 0; index < values.size(); index++) {
            BigDecimal value = values.get(index) == null ? BigDecimal.ZERO : values.get(index);
            int x = left + index * (width - left - right) / Math.max(values.size() - 1, 1);
            int y = top + max.subtract(value).multiply(BigDecimal.valueOf(height - top - bottom)).divide(range, 6, java.math.RoundingMode.HALF_UP).intValue();
            if (index > 0) graphics.drawLine(previousX, previousY, x, y);
            previousX = x; previousY = y;
        }
        graphics.fillOval(previousX - 4, previousY - 4, 8, 8); graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", png);
        XWPFRun run = document.createParagraph().createRun();
        try {
            run.addPicture(new ByteArrayInputStream(png.toByteArray()), Document.PICTURE_TYPE_PNG, label + ".png", width * 9525, height * 9525);
        } catch (InvalidFormatException exception) {
            throw new IOException("재무 차트 이미지를 문서에 추가할 수 없습니다.", exception);
        }
    }

    private void scenarioTable(XWPFDocument document, List<StressScenario> rows) {
        XWPFTable table = document.createTable((rows == null ? 0 : rows.size()) + 1, 4);
        String[] headers = {"시나리오", "손익분기점", "총 영업이익", "필요 운전자금"};
        for (int index = 0; index < headers.length; index++) table.getRow(0).getCell(index).setText(headers[index]);
        if (rows != null) for (int row = 0; row < rows.size(); row++) {
            StressScenario value = rows.get(row); String[] cells = {scenarioLabel(value.code(), value.label()),
                value.breakEvenMonth() == null ? "분석 기간 내 미도달" : value.breakEvenMonth() + "개월 차", money(value.totalOperatingProfit()), money(value.requiredWorkingCapital())};
            for (int column = 0; column < cells.length; column++) table.getRow(row + 1).getCell(column).setText(cells[column]);
        }
        styleTable(table);
    }

    private void scenarioChart(XWPFDocument document, List<StressScenario> scenarios) throws IOException {
        if (scenarios == null || scenarios.isEmpty()) return;
        List<List<ChartPoint>> series = scenarios.stream().map(StressScenario::monthlyCashFlow).filter(values -> values != null && !values.isEmpty()).toList();
        if (series.isEmpty()) return;
        paragraph(document, "시나리오별 누적 현금흐름 비교");
        List<BigDecimal> values = series.stream().flatMap(List::stream).map(ChartPoint::cumulativeCashFlow).filter(value -> value != null).toList();
        drawMultiLineChart(document, series, values, "scenario-cash-flow", new Color[] {new Color(224, 90, 71), new Color(36, 95, 192), new Color(22, 130, 108)});
    }

    private void monteCarloRangeChart(XWPFDocument document, FinancialModuleResponse.MonteCarloSummary risk) throws IOException {
        if (risk == null) return;
        paragraph(document, "수익 범위 해석");
        paragraph(document, "P10은 보수적으로 예상한 결과, P50은 가장 가운데(기준) 결과, P90은 상향 가능성을 반영한 결과입니다. 막대가 짧을수록 가정 변화에 따른 수익 범위가 좁습니다.");
        int width = 610, height = 138, left = 54, right = 28, middle = 63;
        BigDecimal min = risk.profitP10().signum() >= 0 ? risk.profitP10().multiply(BigDecimal.valueOf(.9)) : risk.profitP10().multiply(BigDecimal.valueOf(1.1));
        BigDecimal max = risk.profitP90().signum() >= 0 ? risk.profitP90().multiply(BigDecimal.valueOf(1.1)) : risk.profitP90().multiply(BigDecimal.valueOf(.9));
        if (min.compareTo(max) == 0) { min = min.subtract(BigDecimal.ONE); max = max.add(BigDecimal.ONE); }
        final BigDecimal axisMin = min; final BigDecimal axisRange = max.subtract(axisMin); BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
        java.util.function.Function<BigDecimal, Integer> x = value -> left + value.subtract(axisMin).multiply(BigDecimal.valueOf(width - left - right)).divide(axisRange, 6, java.math.RoundingMode.HALF_UP).intValue();
        graphics.setColor(new Color(238, 249, 245)); graphics.fillRoundRect(left, middle - 17, width - left - right, 34, 17, 17);
        graphics.setColor(new Color(219, 232, 227)); graphics.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); graphics.drawLine(x.apply(risk.profitP10()), middle, x.apply(risk.profitP90()), middle);
        BigDecimal[] points = {risk.profitP10(), risk.profitP50(), risk.profitP90()}; String[] labels = {"P10", "P50", "P90"}; Color[] colors = {new Color(224, 90, 71), new Color(36, 95, 192), new Color(22, 130, 108)};
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int index = 0; index < points.length; index++) { int pointX = x.apply(points[index]); graphics.setColor(colors[index]); graphics.fillOval(pointX - 6, middle - 6, 12, 12); graphics.drawString(labels[index], Math.max(4, Math.min(width - right - 22, pointX - 10)), middle - 29); graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9)); graphics.drawString(compactAxisNumber(points[index]), Math.max(4, Math.min(width - right - 42, pointX - 20)), middle + 34); graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10)); }
        graphics.setColor(new Color(119, 132, 127)); graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10)); graphics.drawString(axisNumber(min), left, height - 12); graphics.drawString(axisNumber(max), width - right - 62, height - 12); graphics.dispose();
        addImage(document, image, "monte-carlo-range", width, height);
    }

    private void drawMultiLineChart(XWPFDocument document, List<List<ChartPoint>> series, List<BigDecimal> values, String name, Color[] colors) throws IOException {
        int width = 610, height = 205, left = 58, right = 20, top = 24, bottom = 32; BigDecimal actualMin = values.stream().reduce(BigDecimal::min).orElse(BigDecimal.ZERO); BigDecimal actualMax = values.stream().reduce(BigDecimal::max).orElse(BigDecimal.ZERO);
        BigDecimal min = actualMin.signum() >= 0 ? actualMin.multiply(BigDecimal.valueOf(.9)) : actualMin.multiply(BigDecimal.valueOf(1.1)); BigDecimal max = actualMax.signum() <= 0 ? actualMax.multiply(BigDecimal.valueOf(.9)) : actualMax.multiply(BigDecimal.valueOf(1.1)); if (min.compareTo(max) == 0) { min = min.subtract(BigDecimal.ONE); max = max.add(BigDecimal.ONE); } BigDecimal range = max.subtract(min);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); Graphics2D graphics = image.createGraphics(); graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
        int zeroY = top + max.multiply(BigDecimal.valueOf(height - top - bottom)).divide(range, 6, java.math.RoundingMode.HALF_UP).intValue(); graphics.setColor(new Color(229, 239, 235)); graphics.drawLine(left, top, width - right, top); if (zeroY >= top && zeroY <= height - bottom) graphics.drawLine(left, zeroY, width - right, zeroY); graphics.drawLine(left, height - bottom, width - right, height - bottom);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10)); graphics.setColor(new Color(119, 132, 127)); graphics.drawString(axisNumber(max), 4, top + 4); if (zeroY >= top && zeroY <= height - bottom) graphics.drawString("0", 35, zeroY + 4); graphics.drawString(axisNumber(min), 4, height - bottom + 4); graphics.drawString("M1", left, height - 12);
        for (int line = 0; line < series.size(); line++) { List<ChartPoint> rows = series.get(line); graphics.setColor(colors[line % colors.length]); graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); int previousX = 0, previousY = 0; for (int index = 0; index < rows.size(); index++) { int x = left + index * (width - left - right) / Math.max(rows.size() - 1, 1); BigDecimal value = rows.get(index).cumulativeCashFlow() == null ? BigDecimal.ZERO : rows.get(index).cumulativeCashFlow(); int y = top + max.subtract(value).multiply(BigDecimal.valueOf(height - top - bottom)).divide(range, 6, java.math.RoundingMode.HALF_UP).intValue(); if (index > 0) graphics.drawLine(previousX, previousY, x, y); previousX = x; previousY = y; } }
        graphics.dispose(); addImage(document, image, name, width, height);
    }

    private void addImage(XWPFDocument document, BufferedImage image, String name, int width, int height) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", png); XWPFRun run = document.createParagraph().createRun(); try { run.addPicture(new ByteArrayInputStream(png.toByteArray()), Document.PICTURE_TYPE_PNG, name + ".png", width * 9525, height * 9525); } catch (InvalidFormatException exception) { throw new IOException("재무 차트 이미지를 문서에 추가할 수 없습니다.", exception); }
    }

    private void monteCarloTable(XWPFDocument document, FinancialModuleResponse result) {
        var risk = result.monteCarlo();
        if (risk == null) { paragraph(document, "시뮬레이션 결과가 없습니다."); return; }
        paragraph(document, "가격·판매량·비용을 바꾸어 " + number(risk.simulations()) + "회 계산한 결과입니다. P10은 보수적, P50은 기준, P90은 낙관적 결과입니다.");
        keyValues(document, List.of(
            new String[] {"보수적 결과 (P10)", money(risk.profitP10())},
            new String[] {"기준 결과 (P50)", money(risk.profitP50())},
            new String[] {"낙관적 결과 (P90)", money(risk.profitP90())},
            new String[] {"손실 확률", percent(risk.lossProbabilityPercent())},
            new String[] {"투자금 회수 가능성", percent(risk.paybackProbabilityPercent())}
        ));
    }

    private void reportSection(XWPFDocument document, String label, List<String> values) {
        paragraph(document, label);
        if (values == null || values.isEmpty()) { paragraph(document, "제공된 내용이 없습니다."); return; }
        for (String value : values) paragraph(document, "• " + localizedReportText(value));
    }

    private void keyValues(XWPFDocument document, List<String[]> rows) {
        XWPFTable table = document.createTable(rows.size(), 2);
        for (int row = 0; row < rows.size(); row++) { table.getRow(row).getCell(0).setText(rows.get(row)[0]); table.getRow(row).getCell(1).setText(rows.get(row)[1]); }
        styleTable(table);
    }

    private void title(XWPFDocument document, String value) { paragraph(document, value, true, 23, MINT_DARK, 0, 100); }
    private void subtitle(XWPFDocument document, String value) { paragraph(document, value, false, 9, "596761", 0, 160); }
    private void heading(XWPFDocument document, String value) { paragraph(document, value, true, 14, MINT_DARK, 220, 80); }
    private void callout(XWPFDocument document, String value) { paragraph(document, "핵심 요약  |  " + value, true, 10, MINT_DARK, 0, 160); }
    private void paragraph(XWPFDocument document, String value) { paragraph(document, value, false, 10, INK, 0, 70); }
    private void paragraph(XWPFDocument document, String value, boolean bold, int size, String color, int before, int after) {
        XWPFParagraph paragraph = document.createParagraph(); paragraph.setSpacingBefore(before); paragraph.setSpacingAfter(after); paragraph.setSpacingBetween(1.12);
        XWPFRun run = paragraph.createRun(); run.setFontFamily("Malgun Gothic"); run.setFontSize(size); run.setBold(bold); run.setColor(color); run.setText(value);
    }
    private void styleTable(XWPFTable table) {
        table.setWidth("100%");
        for (int row = 0; row < table.getRows().size(); row++) for (XWPFTableCell cell : table.getRow(row).getTableCells()) {
            cell.setColor(row == 0 ? MINT : "FFFFFF");
            for (XWPFParagraph paragraph : cell.getParagraphs()) for (XWPFRun run : paragraph.getRuns()) {
                run.setFontFamily("Malgun Gothic"); run.setFontSize(9); run.setBold(row == 0); run.setColor(row == 0 ? MINT_DARK : INK);
            }
        }
    }
    private String scenarioLabel(String code, String label) { return switch (code == null ? "" : code) { case "CONSERVATIVE" -> "보수적"; case "BASE" -> "기준"; case "OPTIMISTIC" -> "낙관적"; default -> text(label, code); }; }
    private String money(BigDecimal value) { return value == null ? "-" : number(value) + " KRW"; }
    private String percent(BigDecimal value) { return value == null ? "-" : value.stripTrailingZeros().toPlainString() + "%"; }
    private String number(Number value) { return value == null ? "-" : NumberFormat.getNumberInstance(Locale.KOREA).format(value); }
    private String axisNumber(BigDecimal value) { return number(value.setScale(0, java.math.RoundingMode.HALF_UP)); }
    private String compactAxisNumber(BigDecimal value) {
        BigDecimal absolute = value.abs(); String sign = value.signum() < 0 ? "-" : "";
        if (absolute.compareTo(BigDecimal.valueOf(1_000_000_000L)) >= 0) return sign + absolute.divide(BigDecimal.valueOf(1_000_000_000L), 1, java.math.RoundingMode.HALF_UP) + "B";
        if (absolute.compareTo(BigDecimal.valueOf(1_000_000L)) >= 0) return sign + absolute.divide(BigDecimal.valueOf(1_000_000L), 1, java.math.RoundingMode.HALF_UP) + "M";
        return axisNumber(value);
    }
    private String localizedReportText(String value) {
        if (value == null) return "";
        return switch (value) {
            case "Base scenario is profitable over the selected period." -> "기준 시나리오에서 분석 기간 동안 누적 영업이익이 흑자입니다.";
            case "Base scenario remains loss-making over the selected period." -> "기준 시나리오에서 분석 기간 동안 누적 영업이익이 적자입니다.";
            case "P10/P50/P90 profit should be reviewed before funding decisions." -> "자금 조달 전에는 보수·기준·낙관 범위(P10/P50/P90)의 수익 가능성을 함께 검토하세요.";
            case "Validate price, volume and variable-cost assumptions with observed data." -> "가격·판매량·변동비 가정을 실제 고객·판매 데이터로 검증하세요.";
            case "Use the conservative scenario for cash planning." -> "현금 계획은 보수 시나리오를 기준으로 수립하세요.";
            case "This module is a planning simulation based on supplied assumptions, not investment advice or a revenue guarantee." -> "이 모듈은 입력 가정에 따른 계획 시뮬레이션이며 투자 조언이나 수익 보장이 아닙니다.";
            default -> value.replace("Total revenue:", "총매출:").replace("Operating profit:", "영업이익:").replace("Required working capital:", "필요 운전자금:").replace("Monte Carlo loss probability:", "몬테카를로 손실 확률:");
        };
    }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private enum ChartMetric { REVENUE, CUMULATIVE_CASH }
}
