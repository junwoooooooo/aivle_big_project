package com.aivle.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.jobevent.JobEventStreamService;
import com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorker;
import com.aivle.backend.pipeline.idea.worker.IdeaBriefDerivationWorker;
import com.aivle.backend.pipeline.marketing.worker.MarketingContentWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "app.scheduling.enabled=true",
    "app.task-run.idea-brief-poll-interval-ms=3600000",
    "app.task-run.idea-brief-recovery-interval-ms=3600000",
    "app.task-run.concept-factory-poll-interval-ms=3600000",
    "app.task-run.concept-factory-recovery-interval-ms=3600000",
    "app.task-run.marketing-content-poll-interval-ms=3600000",
    "app.task-run.marketing-content-recovery-interval-ms=3600000",
    "app.job-events.heartbeat-ms=3600000"
})
@ActiveProfiles("test")
class AsyncExecutionConfigurationTests {
    @Autowired ApplicationContext context;

    @Test
    void applicationContextEnablesConcurrentPipelineScheduling() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty();
        assertThat(context.getBean("taskScheduler")).isInstanceOf(ThreadPoolTaskScheduler.class);
        assertThat(context.getBean(IdeaBriefDerivationWorker.class)).isNotNull();
        assertThat(context.getBean(ConceptFactoryWorker.class)).isNotNull();
        assertThat(context.getBean(MarketingContentWorker.class)).isNotNull();
        assertThat(context.getBean(JobEventStreamService.class)).isNotNull();

        ThreadPoolTaskScheduler scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
        assertThat(scheduler.getPoolSize()).isGreaterThan(1);
    }
}
