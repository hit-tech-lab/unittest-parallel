package com.example.parallel.fx;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpExchangeRateClient implements ExchangeRateClient {

    private final RestClient restClient;

    public HttpExchangeRateClient(@Value("${fx.base-url:http://fx.invalid}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @Override
    public BigDecimal rate(String fromCurrency, String toCurrency) {
        String body = restClient.get()
                .uri("/rates/{from}/{to}", fromCurrency, toCurrency)
                .retrieve()
                .body(String.class);
        return new BigDecimal(body.trim());
    }
}
