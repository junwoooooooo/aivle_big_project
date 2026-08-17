package com.aivle.backend.pipeline.marketing.strategy.application;

import com.aivle.backend.pipeline.marketing.strategy.domain.MarketingStrategyReport;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MarketingStrategyPdfService {

    private final ObjectMapper mapper;

    public MarketingStrategyPdfService(
        ObjectMapper mapper
    ) {
        this.mapper = mapper;
    }

    public byte[] render(
        MarketingStrategyReport report
    ) {
        JsonNode result =
            mapper.readTree(report.getResultJson());

        String html = buildHtml(report, result);

        try (ByteArrayOutputStream output =
                new ByteArrayOutputStream()) {
            PdfRendererBuilder builder =
                new PdfRendererBuilder();

            builder.useFastMode();

            builder.useFont(
                () -> font(
                    "fonts/NotoSansKR-Regular.ttf"
                ),
                "Noto Sans KR",
                400,
                FontStyle.NORMAL,
                true
            );

            builder.useFont(
                () -> font(
                    "fonts/NotoSansKR-Bold.ttf"
                ),
                "Noto Sans KR",
                700,
                FontStyle.NORMAL,
                true
            );

            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.run();

            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException(
                "마케팅 전략 PDF를 생성할 수 없습니다.",
                error
            );
        }
    }

    private String buildHtml(
        MarketingStrategyReport report,
        JsonNode result
    ) {
        StringBuilder html = new StringBuilder();

        html.append("""
            <!DOCTYPE html>
            <html lang="ko">
            <head>
              <meta charset="UTF-8"/>
              <style>
                @page {
                  size: A4;
                  margin: 18mm 16mm;
                }

                body {
                  font-family: 'Noto Sans KR';
                  color: #1f2b29;
                  font-size: 10.5pt;
                  line-height: 1.65;
                }

                h1 {
                  color: #08766a;
                  font-size: 24pt;
                  margin: 0 0 6mm 0;
                }

                h2 {
                  margin-top: 8mm;
                  padding-bottom: 2mm;
                  border-bottom: 2px solid #0f8878;
                  color: #075e55;
                  font-size: 15pt;
                }

                h3 {
                  margin-bottom: 1mm;
                  color: #1d665d;
                  font-size: 11pt;
                }

                .meta {
                  padding: 4mm;
                  border: 1px solid #b7d9d4;
                  background: #eefbf8;
                }

                .summary {
                  padding: 5mm;
                  border-left: 5px solid #0f8878;
                  background: #f5fbfa;
                }

                .card {
                  margin: 3mm 0;
                  padding: 4mm;
                  border: 1px solid #d3e3df;
                  border-radius: 3mm;
                  page-break-inside: avoid;
                }

                ul {
                  margin-top: 1mm;
                  padding-left: 6mm;
                }

                .footer {
                  margin-top: 10mm;
                  color: #667572;
                  font-size: 8.5pt;
                }
              </style>
            </head>
            <body>
            """);

        html.append("<h1>마케팅 전략 보고서</h1>");

        html.append("<div class=\"meta\">")
            .append("<strong>전략 ID:</strong> ")
            .append(escape(report.getId()))
            .append("<br/><strong>생성 시각:</strong> ")
            .append(escape(
                report.getGeneratedAt().toString()
            ))
            .append("<br/><strong>입력 해시:</strong> ")
            .append(escape(
                report.getSourceManifestHash()
            ))
            .append("</div>");

        html.append("<h2>1. 핵심 요약</h2>")
            .append("<div class=\"summary\">")
            .append(escape(
                result.path("executiveSummary")
                    .asText()
            ))
            .append("</div>");

        html.append("<h2>2. 타깃 고객</h2>");
        appendList(
            html,
            result.path("targetCustomers")
        );

        html.append("<h2>3. 포지셔닝</h2>")
            .append("<p>")
            .append(escape(
                result.path("positioning").asText()
            ))
            .append("</p>");

        html.append("<h2>4. 핵심 메시지</h2>");
        appendList(
            html,
            result.path("coreMessages")
        );

        html.append("<h2>5. 채널 전략</h2>");

        for (JsonNode channel :
                result.path("channelStrategies")) {
            html.append("<div class=\"card\">")
                .append("<h3>")
                .append(escape(
                    channel.path("channel").asText()
                ))
                .append("</h3>")
                .append("<p><strong>목적:</strong> ")
                .append(escape(
                    channel.path("objective").asText()
                ))
                .append("</p>")
                .append("<p><strong>대상:</strong> ")
                .append(escape(
                    channel.path("audience").asText()
                ))
                .append("</p>")
                .append("<p><strong>선정 근거:</strong> ")
                .append(escape(
                    channel.path("rationale").asText()
                ))
                .append("</p>")
                .append("<strong>실행 항목</strong>");

            appendList(
                html,
                channel.path("actions")
            );

            html.append("<strong>KPI</strong>");
            appendList(
                html,
                channel.path("kpis")
            );

            html.append("</div>");
        }

        html.append("<h2>6. 콘텐츠 축</h2>");
        appendList(
            html,
            result.path("contentPillars")
        );

        html.append("<h2>7. 캠페인 로드맵</h2>");

        for (JsonNode phase :
                result.path("campaignRoadmap")) {
            html.append("<div class=\"card\">")
                .append("<h3>")
                .append(escape(
                    phase.path("phase").asText()
                ))
                .append("</h3>")
                .append("<p><strong>목표:</strong> ")
                .append(escape(
                    phase.path("objective").asText()
                ))
                .append("</p>")
                .append("<strong>실행 항목</strong>");

            appendList(
                html,
                phase.path("actions")
            );

            html.append("<strong>KPI</strong>");
            appendList(
                html,
                phase.path("kpis")
            );

            html.append("</div>");
        }

        html.append("<h2>8. 예산 운영 기준</h2>");
        appendList(
            html,
            result.path("budgetGuidelines")
        );

        html.append("<h2>9. 위험 및 주의사항</h2>");
        appendList(
            html,
            result.path("risks")
        );

        html.append("<h2>10. 근거 참조</h2>");
        appendList(
            html,
            result.path("evidenceRefs")
        );

        html.append("""
              <p class="footer">
                이 보고서는 현재 사업안과 이번 전략 입력에 실제로 포함된
                분석 결과를 기반으로 생성되었습니다.
                외부 사실이나 수치를 임의로 추가하지 않습니다.
              </p>
            </body>
            </html>
            """);

        return html.toString();
    }

    private void appendList(
        StringBuilder html,
        JsonNode values
    ) {
        html.append("<ul>");

        if (values.isArray()) {
            for (JsonNode value : values) {
                html.append("<li>")
                    .append(escape(value.asText()))
                    .append("</li>");
            }
        }

        html.append("</ul>");
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(
            value == null ? "" : value
        );
    }

    private InputStream font(String path) {
        boolean bold = path.contains("Bold");
        var candidates = bold
            ? java.util.List.of(
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Bold.otf",
                "C:/Windows/Fonts/malgunbd.ttf")
            : java.util.List.of(
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Regular.otf",
                "C:/Windows/Fonts/malgun.ttf");
        for (String candidate : candidates) {
            try {
                if (Files.isRegularFile(Path.of(candidate))) return new FileInputStream(candidate);
            } catch (Exception ignored) {
                // Try the next OS-provided Korean font. No user content is involved.
            }
        }
        throw new IllegalStateException("PDF용 한국어 폰트를 찾을 수 없습니다: " + path);
    }
}
