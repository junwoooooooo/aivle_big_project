package com.aivle.backend.pipeline.finalreport.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.service.TaskRunService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class FinalBusinessProposalWorkerTransactionTests {
    @Test
    void eventPublishUsesAnIndependentWritableTransaction() throws Exception {
        Transactional boundary = FinalReportService.class
            .getMethod("publish", Long.class, String.class, String.class, String.class,
                JobEvent.Status.class, String.class)
            .getAnnotation(Transactional.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.readOnly()).isFalse();
        assertThat(boundary.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void proposalAndReviewWorkersIgnoreEventTransportFailure() {
        FinalReportService reports = mock(FinalReportService.class);
        doThrow(new IllegalStateException("event storage unavailable")).when(reports)
            .publish(any(), any(), any(), any(), any(), any());
        var tasks = mock(TaskRunService.class);
        var ai = mock(InternalAiExecutionClient.class);
        var proposal = new FinalBusinessProposalWorker(tasks, ai, reports);
        var review = new FinalBusinessProposalReviewWorker(tasks, ai, reports);

        assertThatCode(() -> proposal.safePublish(41L, "task-1", "COMPOSING",
            "job.final-report.composing", JobEvent.Status.RUNNING, null)).doesNotThrowAnyException();
        assertThatCode(() -> review.safePublish(41L, "task-2", "REVIEWING",
            "job.final-report.review.running", JobEvent.Status.RUNNING, null)).doesNotThrowAnyException();
    }

    @Test
    void proposalQueuesIdempotentAutoReviewAndReviewQueueFailureIsNonBlocking() {
        FinalReportService reports = mock(FinalReportService.class);
        var proposal = new FinalBusinessProposalWorker(mock(TaskRunService.class),
            mock(InternalAiExecutionClient.class), reports);

        proposal.safeStartReview(7L, 41L, "snapshot-1");
        verify(reports).startReview(7L, 41L, "snapshot-1",
            "auto-review:snapshot-1", "auto-review:snapshot-1");

        doThrow(new IllegalStateException("review queue unavailable")).when(reports)
            .startReview(any(), any(), any(), any(), any());
        assertThatCode(() -> proposal.safeStartReview(7L, 41L, "snapshot-2"))
            .doesNotThrowAnyException();
    }
}
