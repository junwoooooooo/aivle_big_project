package com.aivle.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTests {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesOneRequestIdAndSharesItWithTheRequestAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, ignored) -> {
            String first = (String) filteredRequest.getAttribute(RequestIds.ATTRIBUTE);
            observed.set(RequestIds.resolve((MockHttpServletRequest) filteredRequest));
            assertThat(observed.get()).isEqualTo(first);
        });

        assertThat(observed.get()).isEqualTo(response.getHeader(RequestIds.HEADER));
        assertThat(UUID.fromString(observed.get()).toString()).isEqualTo(observed.get());
    }

    @Test
    void preservesValidCallerRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "market-request_01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, ignored) ->
            assertThat(filteredRequest.getAttribute(RequestIds.ATTRIBUTE))
                .isEqualTo("market-request_01"));

        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("market-request_01");
    }

    @Test
    void replacesInvalidOrOversizedCallerRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "x".repeat(129));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, ignored) -> { });

        String resolved = response.getHeader(RequestIds.HEADER);
        assertThat(resolved).isNotEqualTo("x".repeat(129)).hasSize(36);
        assertThat(request.getAttribute(RequestIds.ATTRIBUTE)).isEqualTo(resolved);
    }
}
