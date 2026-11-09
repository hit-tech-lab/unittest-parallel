package demo.demo;

import demo.project.ProjectBaseIT;
import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Negative control: {@code @PerTestMock} cấp class KHÔNG nêu kiểu bean (chỉ field mới được bỏ trống kiểu) => INVALID_DECLARATION. */
@PerTestMock
class DeclInvalidDeclarationDemo extends ProjectBaseIT {

    @Test
    void test() {
    }
}
