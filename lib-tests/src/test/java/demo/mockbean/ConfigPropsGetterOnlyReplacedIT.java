package demo.mockbean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Như {@link ConfigPropsGetterOnlyMockOnlyIT} nhưng bean ĐÃ có trong context (định nghĩa bị thay): context phải khởi động, handle stub được và
 * {@code @Primary}/{@code @Qualifier} của định nghĩa gốc còn nguyên.
 */
@PerTestMock(CodeProvider.class)
@SpringBootTest(classes = ReplacedConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "mbx.code=abc")
@ParallelSpringTest(context = "mockbean-props-getter-replaced")
class ConfigPropsGetterOnlyReplacedIT {

    @Autowired ApplicationContext ctx;
    @Autowired CodeProvider injected;
    @Autowired @Qualifier("codes") CodeProvider byQualifier;
    @PerTestMock CodeProvider handle;

    @Test
    void contextStartsAndTheHandleStubsTheGetter() {
        when(handle.getCode()).thenReturn("stubbed");

        assertThat(injected.getCode()).isEqualTo("stubbed");
        assertThat(byQualifier).isSameAs(injected);
    }

    @Test
    void primaryAndQualifierOfTheReplacedDefinitionSurvive() {
        var factory = ((ConfigurableApplicationContext) ctx).getBeanFactory();

        assertThat(factory.getBeanDefinition("codeProvider").isPrimary()).isTrue();
        assertThat(ctx.getBeanNamesForType(CodeProvider.class)).containsExactly("codeProvider");
    }
}
