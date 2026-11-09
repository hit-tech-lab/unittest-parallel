package com.hit.ptest.propagation;

import com.hit.ptest.ParallelTestException;
import com.hit.ptest.intercept.TestBinding;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gắn binding cho thread HTTP của server thật (RANDOM_PORT) khi request mang header {@value TestBinding#HEADER}. */
public final class BindingFilter extends OncePerRequestFilter implements Ordered {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(TestBinding.HEADER);
        if (id == null) {
            chain.doFilter(request, response);
            return;
        }
        TestBinding binding = TestBinding.find(id);
        if (binding == null) {
            throw new ServletException(new ParallelTestException.Config("BINDING_EXPIRED: no active test binding '" + id + "'"));
        }
        TestBinding previous = TestBinding.bind(binding);
        try {
            chain.doFilter(request, response);
        } finally {
            TestBinding.bind(previous);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
