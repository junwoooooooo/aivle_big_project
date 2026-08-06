package com.aivle.backend.persona.catalog.application;

import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.dto.BaselinePersonaResponse;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BaselinePersonaQueryService {
    private final BaselinePersonaRepository personas;

    public List<BaselinePersonaResponse> catalog() {
        return personas.findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
            BaselinePersonaCatalog.VERSION).stream()
            .map(item -> new BaselinePersonaResponse(
                item.getId(), item.getPersonaCode(), item.getClusterId(),
                item.getDisplayName(), item.getShortName(), item.getDescription(),
                item.getAgeGroup(), item.getGender(), item.getSampleSize(),
                item.getWeightedShare(), item.getDataSource(), item.getDataVersion(),
                item.getCatalogVersion(), item.getKeyTraitsJson(),
                item.getDemographicSummaryJson(), item.getEvidenceMetricsJson(),
                item.getLimitationsJson()))
            .toList();
    }
}
