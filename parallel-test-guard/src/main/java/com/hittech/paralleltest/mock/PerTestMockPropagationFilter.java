package com.hittech.paralleltest.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.hittech.paralleltest.core.TestBindings;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Registered by the guard in web test contexts: re-binds the calling test (mocks + identity) on the server thread. */
public class PerTestMockPropagationFilter extends OncePerRequestFilter implements Ordered {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TestBindings.Binding binding = TestBindings.byId(request.getHeader(PerTestMocks.HEADER));
        if (binding == null) {
            chain.doFilter(request, response); // e.g. MockMvc: already on the test thread
            return;
        }
        try {
            TestBindings.callWith(binding, () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException | RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /** Also re-bind on async and error dispatches (they run on other container threads). */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
