package demo.it;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import io.hit.ptest.propagation.BindingHeaderInterceptor;

/** D: server RANDOM_PORT; request chạy trên thread của Tomcat, nhận binding qua header. */
abstract class HttpBase extends MainIT {

    @Value("${local.server.port}") int port;

    protected RestClient withBinding() {
        return RestClient.builder().baseUrl("http://localhost:" + port).requestInterceptor(new BindingHeaderInterceptor()).build();
    }

    protected RestClient withoutBinding() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }
}
