package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.PlanSectionType;

import java.util.List;
import java.util.Set;

public record BusinessPlanSectionDefinition(
        BusinessPlanSectionCode code,
        String displayName,
        int sequence,
        boolean required,
        String description,
        List<String> aliases,
        AllowedMissingPolicy allowedMissingPolicy,
        Set<PlanSectionType> mappedPlanSectionTypes
) {
    public BusinessPlanSectionDefinition {
        if (code == null
                || displayName == null
                || displayName.isBlank()
                || sequence <= 0
                || description == null
                || allowedMissingPolicy == null) {
            throw new IllegalArgumentException("section definition fields are required");
        }
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        mappedPlanSectionTypes = mappedPlanSectionTypes == null
                ? Set.of()
                : Set.copyOf(mappedPlanSectionTypes);
    }
}
