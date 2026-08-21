package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.ConfirmHypothesesRequest;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ConceptRefinementApplyServiceContractTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConceptPortfolioSelectionService selections = mock(ConceptPortfolioSelectionService.class);
    private final ConceptRefinementApplyService service = new ConceptRefinementApplyService(
        selections, mock(BmPlanPreparationService.class), mock(ConceptRefinementFinalRepository.class), mapper);

    @Test
    void channelsArrayIsCanonicalBeforeConfirmPersistenceBoundary() {
        var proposals = mapper.createArrayNode();
        proposals.addObject().put("fieldKey", "channels").set("proposedValue",
            mapper.readTree("[\"웹\",\"파트너 판매\"]"));

        service.apply(7L, 42L, 17L, proposals, "refinement-key");

        ArgumentCaptor<ConfirmHypothesesRequest> request = ArgumentCaptor.forClass(ConfirmHypothesesRequest.class);
        verify(selections).confirm(eq(7L), eq(42L), eq(17L), request.capture());
        assertThat(request.getValue().changes().path("CHANNELS").isTextual()).isTrue();
        assertThat(request.getValue().changes().path("CHANNELS").asText()).isEqualTo("웹, 파트너 판매");
    }

    @Test
    void nestedRefinementValueIsRejectedInsteadOfStringified() {
        var proposals = mapper.createArrayNode();
        proposals.addObject().put("fieldKey", "channels").set("proposedValue",
            mapper.readTree("[\"웹\",{\"nested\":true}]"));

        assertThatThrownBy(() -> service.apply(7L, 42L, 17L, proposals, "invalid-key"))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(selections);
    }
}
