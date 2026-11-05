package com.hittech.paralleltest.mock;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/** HTTP propagation of the per-test mock binding (requires spring-web). */
public final class PerTestMockHttp {

    private PerTestMockHttp() {
    }

    /**
     * Interceptor for RestClient / RestTemplate calling the embedded server:
     * {@code RestClient.builder().requestInterceptor(PerTestMockHttp.propagation())}.
     */
    public static ClientHttpRequestInterceptor propagation() {
        return (request, body, execution) -> {
            PerTestMocks.currentTestId().ifPresent(id -> request.getHeaders().set(PerTestMocks.HEADER, id));
            return execution.execute(request, body);
        };
    }
}
