package com.aivle.backend.document.application;

import com.aivle.backend.document.entity.MissingField;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.document.entity.StructuredPlanSection;
import com.aivle.backend.document.structure.BusinessPlanSectionCatalog;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.document.structure.StructuredItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StructuredPlanCompletionService {
    private final BusinessPlanSectionCatalog catalog;

    public void recalculate(
        StructuredPlan plan,
        List<StructuredPlanSection> sections,
        List<MissingField> missingFields
    ) {
        int completed = 0;
        for (var definition : catalog.all()) {
            if (definition.required()
                && isComplete(definition.code(), sections, missingFields)) {
                completed++;
            }
        }
        int required = Math.toIntExact(catalog.all().stream()
            .filter(definition -> definition.required())
            .count());
        plan.recalculateCompletion(completed, required);
    }

    private boolean isComplete(
        BusinessPlanSectionCode sectionCode,
        List<StructuredPlanSection> sections,
        List<MissingField> missingFields
    ) {
        boolean aiPresent = sections.stream()
            .filter(section -> sectionCode.equals(section.getSectionCode()))
            .anyMatch(section ->
                section.getItemStatus() == StructuredItemStatus.PRESENT);
        if (aiPresent) {
            return true;
        }
        List<MissingField> linkedRequired = missingFields.stream()
            .filter(MissingField::getRequired)
            .filter(field -> sectionCode.equals(field.getSectionCode()))
            .toList();
        return !linkedRequired.isEmpty()
            && linkedRequired.stream().allMatch(MissingField::isResolved);
    }
}
