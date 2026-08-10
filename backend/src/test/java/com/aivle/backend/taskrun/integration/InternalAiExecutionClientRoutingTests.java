package com.aivle.backend.taskrun.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.TaskType;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class InternalAiExecutionClientRoutingTests {
    @Test
    void routesOnlyConceptPortfolioV2ToLongReadClient() {
        RestClient normal = RestClient.builder().build();
        RestClient longRead = RestClient.builder().build();
        AiServerProperties properties = new AiServerProperties("http://localhost",
            Duration.ofSeconds(3), Duration.ofSeconds(30), Duration.ofMinutes(15), "token");
        InternalAiExecutionClient client = new InternalAiExecutionClient(
            normal, longRead, properties, new ObjectMapper());

        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_RUN)).isSameAs(longRead);
        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE)).isSameAs(longRead);
        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION)).isSameAs(longRead);
        assertThat(client.clientFor(TaskType.IDEA_BRIEF_DERIVATION)).isSameAs(normal);
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.conceptPortfolioReadTimeout()).isEqualTo(Duration.ofMinutes(15));
    }
}
