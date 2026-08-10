package com.aivle.backend.integration.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServerClientConfiguration {

    @Bean
    @Qualifier("aiServerRestClient")
    RestClient aiServerRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.readTimeout());
    }

    @Bean
    @Qualifier("conceptPortfolioAiServerRestClient")
    RestClient conceptPortfolioAiServerRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.conceptPortfolioReadTimeout());
    }

    RestClient createRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.readTimeout());
    }

    RestClient createRestClient(AiServerProperties properties, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
            properties.connectTimeout()
        );
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
            )
            .build();
    }
}
