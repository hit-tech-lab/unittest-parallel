package demo.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectExceptionsRealIT extends ProjectBaseIT {

    @Test
    void realProviderWhileAnotherClassMocksItInTheSameContext() {
        meetInSameContext("exceptions", 2);

        assertThat(translator.translate("E1")).isEqualTo("[real:E1]");
    }
}
