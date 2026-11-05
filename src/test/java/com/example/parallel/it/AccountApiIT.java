package com.example.parallel.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.parallel.support.TestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AccountApiIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    private String body(String owner, String currency, String balance) {
        return """
                {"owner":"%s","currency":"%s","balance":%s}""".formatted(owner, currency, balance);
    }

    /** Repeated concurrently: each repetition only looks up the id it created itself. */
    @RepeatedTest(20)
    void createAndReadBack() throws Exception {
        String owner = TestData.uniqueOwner("api");
        String json = mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(body(owner, "USD", "12.5")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.id");

        mvc.perform(get("/accounts/{id}", id.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(owner))
                .andExpect(jsonPath("$.balance").value(12.5));
    }

    @Test
    void validationError() throws Exception {
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "TOOLONG", "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFound() throws Exception {
        mvc.perform(get("/accounts/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }
}
