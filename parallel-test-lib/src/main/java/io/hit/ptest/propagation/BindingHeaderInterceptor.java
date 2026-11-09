package io.hit.ptest.propagation;

import io.hit.ptest.intercept.TestBinding;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Gắn header binding vào request của RestClient/RestTemplate để thread HTTP của server nhận được binding của test. */
public final class BindingHeaderInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        TestBinding binding = TestBinding.current();
        if (binding != null) {
            request.getHeaders().set(TestBinding.HEADER, binding.id());
        }
        return execution.execute(request, body);
    }
}
