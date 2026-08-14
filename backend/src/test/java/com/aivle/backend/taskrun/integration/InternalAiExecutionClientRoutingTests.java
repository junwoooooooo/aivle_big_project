package com.aivle.backend.taskrun.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.TaskType;
import java.time.Duration;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;

class InternalAiExecutionClientRoutingTests {
    @Test
    void routesOnlySpecializedTasksToTheirDedicatedReadClients() {
        RestClient normal = RestClient.builder().build();
        RestClient longRead = RestClient.builder().build();
        RestClient marketRead = RestClient.builder().build();
        RestClient conceptRead = RestClient.builder().build();
        RestClient twinRead = RestClient.builder().build();
        AiServerProperties properties = new AiServerProperties("http://localhost",
            Duration.ofSeconds(3), Duration.ofSeconds(30), Duration.ofMinutes(15),
            Duration.ofMinutes(14), "token");
        InternalAiExecutionClient client = new InternalAiExecutionClient(
            normal, longRead, marketRead, conceptRead, twinRead, properties, new ObjectMapper());

        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_RUN)).isSameAs(conceptRead);
        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE)).isSameAs(conceptRead);
        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION)).isSameAs(conceptRead);
        assertThat(client.clientFor(TaskType.TWIN_SURVEY)).isSameAs(twinRead);
        assertThat(client.clientFor(TaskType.MARKET_RESEARCH)).isSameAs(marketRead);
        assertThat(client.clientFor(TaskType.MARKETING_CONTENT_GENERATION)).isSameAs(longRead);
        assertThat(client.clientFor(TaskType.TECH_OPS_ADVISORY)).isSameAs(longRead);
        assertThat(client.clientFor(TaskType.FINANCE_ESTIMATE)).isSameAs(normal);
        assertThat(client.clientFor(TaskType.MARKETING_VISUAL_GENERATION)).isSameAs(normal);
        assertThat(client.clientFor(TaskType.IDEA_BRIEF_DERIVATION)).isSameAs(normal);
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.longReadTimeout()).isEqualTo(Duration.ofMinutes(7));
        assertThat(properties.marketResearchReadTimeout()).isEqualTo(Duration.ofMinutes(22));
        assertThat(properties.conceptPortfolioReadTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.twinSurveyReadTimeout()).isEqualTo(Duration.ofMinutes(14));
    }

    @Test
    void classifiesReadTimeoutSeparatelyFromDependencyConnectionFailure() {
        var timeout = InternalAiExecutionClient.transportFailure(
            new ResourceAccessException("read failed", new SocketTimeoutException("read timed out")));
        var unavailable = InternalAiExecutionClient.transportFailure(
            new ResourceAccessException("connect failed", new ConnectException("connection refused")));

        assertThat(timeout.code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(timeout.reason()).isEqualTo("REQUEST_DEADLINE_EXCEEDED");
        assertThat(unavailable.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(unavailable.reason()).isEqualTo("MODEL_DEPENDENCY_UNAVAILABLE");
    }
}
