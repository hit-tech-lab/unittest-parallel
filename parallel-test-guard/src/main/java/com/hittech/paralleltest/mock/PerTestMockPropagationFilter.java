package com.hittech.paralleltest.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Registered by the guard in web test contexts: re-binds the calling test's mocks on the server thread. */
public class PerTestMockPropagationFilter extends OncePerRequestFilter implements Ordered {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        PerTestMocks.Binding binding = PerTestMocks.byId(request.getHeader(PerTestMocks.HEADER));
        if (binding == null) {
            chain.doFilter(request, response); // e.g. MockMvc: already on the test thread
            return;
        }
        try {
            PerTestMocks.callWith(binding, () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException | RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
