package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;

class BusinessValidationRefinementStarterTests {

    @Test
    void afterCommitListenerStartsASeparateTransaction() throws Exception {
        var method = BusinessValidationRefinementStarter.class
            .getDeclaredMethod("startRoundOne", BusinessValidationCompletedEvent.class);
        var transaction = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void schedulingFailureDoesNotEscapeOrRollBackCompletedBusinessValidation() {
        ConceptRefinementService refinement = mock(ConceptRefinementService.class);
        when(refinement.start(anyLong(), anyLong(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("provider queue unavailable"));
        var starter = new BusinessValidationRefinementStarter(refinement);
        var event = completed();

        assertThatCode(() -> starter.startRoundOne(event)).doesNotThrowAnyException();
        verify(refinement).start(eq(7L), eq(41L), startsWith("bv-refinement-auto-"), eq("session-1"));
    }

    @Test
    void repeatedCompletionUsesTheSameDeterministicIdempotencyKey() {
        ConceptRefinementService refinement = mock(ConceptRefinementService.class);
        var starter = new BusinessValidationRefinementStarter(refinement);

        starter.startRoundOne(completed());
        starter.startRoundOne(completed());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(refinement, times(2)).start(eq(7L), eq(41L), keys.capture(), eq("session-1"));
        org.assertj.core.api.Assertions.assertThat(keys.getAllValues()).hasSize(2).allMatch(keys.getAllValues().get(0)::equals);
    }

    private static BusinessValidationCompletedEvent completed() {
        return new BusinessValidationCompletedEvent("session-1", 41L, 7L, 91L, 92L,
            "seed-1", 31L, 4, 3);
    }
}
