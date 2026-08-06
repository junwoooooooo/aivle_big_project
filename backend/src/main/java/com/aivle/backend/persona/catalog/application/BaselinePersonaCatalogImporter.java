package com.aivle.backend.persona.catalog.application;

import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
public class BaselinePersonaCatalogImporter implements ApplicationRunner {
    private final BaselinePersonaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = repository.countByCatalogVersionAndDeletedAtIsNull(
            BaselinePersonaCatalog.VERSION);
        if (existing == BaselinePersonaCatalog.EXPECTED_PERSONA_COUNT) return;
        if (existing != 0) {
            throw new IllegalStateException("persona catalog is partially imported");
        }
        List<List<String>> rows = readRows();
        if (rows.size() != BaselinePersonaCatalog.EXPECTED_PERSONA_COUNT + 2
            || !rows.get(0).equals(List.of("새 표"))
            || !rows.get(1).equals(List.of("연령", "성별", "N", "유형", "비중", "핵심 특징"))) {
            throw new IllegalStateException("persona catalog resource schema is invalid");
        }
        String age = null;
        String gender = null;
        Integer segmentSampleSize = null;
        Map<String, Integer> segmentOrders = new LinkedHashMap<>();
        List<BaselinePersona> personas = new ArrayList<>();
        for (int rowIndex = 2; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.size() != 6) throw new IllegalStateException("persona catalog row is invalid");
            if (!row.get(0).isBlank()) age = row.get(0);
            if (!row.get(1).isBlank()) gender = row.get(1);
            if (!row.get(2).isBlank()) {
                segmentSampleSize = Integer.valueOf(row.get(2).replace(",", ""));
            }
            if (age == null || gender == null || segmentSampleSize == null) {
                throw new IllegalStateException("persona catalog grouping is invalid");
            }
            int withinSegment = segmentOrders.merge(age + ":" + gender, 1, Integer::sum);
            BigDecimal share = new BigDecimal(row.get(4).replace("%", ""))
                .movePointLeft(2).setScale(4);
            String segmentCode = age.replace("대", "").replace("+", "P")
                + ("여".equals(gender) ? "-F" : "-M");
            String personaCode = "KMP25-" + segmentCode + "-" + twoDigits(withinSegment);
            String displayName = age + " " + ("여".equals(gender) ? "여성" : "남성")
                + " · " + row.get(3);
            personas.add(BaselinePersona.imported(
                personaCode, personaCode, displayName, row.get(3),
                "KMP2025 연령·성별 세그먼트의 가중 K-Means 군집에 "
                    + "분석자가 부여한 해석 라벨",
                age, gender, share, BaselinePersonaCatalog.SOURCE_DATASET,
                BaselinePersonaCatalog.DATA_VERSION, BaselinePersonaCatalog.VERSION,
                json(List.of(row.get(5))),
                json(Map.of("ageGroup", age, "gender", gender,
                    "segmentSampleSize", segmentSampleSize)),
                json(List.of(Map.of("label", "핵심 특징", "value", row.get(5),
                    "sourceType", "DERIVED_FROM_DATA"))),
                json(List.of(
                    "비중은 시장점유율이 아니라 연령·성별 세그먼트 내부 p25wt 가중 비중입니다.",
                    "개별 군집 표본수와 원 K-Means 군집 번호는 제공 집계 CSV에 없습니다.",
                    "Persona 명칭 부여 규칙은 확인되지 않아 해석 라벨로만 취급합니다.")),
                BaselinePersonaCatalog.SOURCE_FILE_HASH, rowIndex - 1));
        }
        validate(personas);
        repository.saveAll(personas);
    }

    private void validate(List<BaselinePersona> personas) {
        if (personas.size() != BaselinePersonaCatalog.EXPECTED_PERSONA_COUNT
            || personas.stream().map(BaselinePersona::getPersonaCode).distinct().count()
                != personas.size()
            || personas.stream().map(BaselinePersona::getClusterId).distinct().count()
                != personas.size()
            || personas.stream().anyMatch(item -> item.getWeightedShare().signum() < 0
                || item.getWeightedShare().compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalStateException("persona catalog quality gate failed");
        }
    }

    private List<List<String>> readRows() {
        try (var input = new ClassPathResource(
            "persona/persona-catalog-v1.csv").getInputStream();
             var reader = new BufferedReader(new InputStreamReader(
                 input, StandardCharsets.UTF_8))) {
            List<List<String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) rows.add(parseCsvLine(line));
            return rows;
        } catch (IOException exception) {
            throw new IllegalStateException("persona catalog resource cannot be read", exception);
        }
    }

    static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("unterminated CSV field");
        values.add(value.toString());
        return values;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("persona catalog JSON cannot be serialized", exception);
        }
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
