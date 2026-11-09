package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Ba test chạy đồng thời trên CÙNG bean CheckoutService thật, mỗi test stub riêng ba cổng cùng kiểu bằng @Qualifier của lớp cơ sở. */
class ProjectCheckoutIT extends ProjectBaseIT {

    @PerTestMock(name = "gatewayA") PaymentGateway gatewayA;
    @PerTestMock(name = "gatewayB") PaymentGateway gatewayB;
    @PerTestMock(name = "gatewayC") PaymentGateway gatewayC;
    @PerTestMock Ports.DocumentDb documentDb;
    @PerTestMock Ports.Mailer mailer;

    @Test
    void firstGatewayFailsSecondIsUsed() {
        when(gatewayA.charge(anyString(), any())).thenThrow(new IllegalStateException("A down"));
        meetInSameContext("checkout", 3);

        assertThat(checkout.pay("o1", BigDecimal.TEN)).isEqualTo("B");

        verify(gatewayA).charge("o1", BigDecimal.TEN);
        verify(gatewayB).charge("o1", BigDecimal.TEN);
        verifyNoInteractions(gatewayC);
        verify(documentDb).save("o1", "B");
        verify(mailer).send("o1");
    }

    @Test
    void firstTwoFailThirdIsUsed() {
        when(gatewayA.charge(anyString(), any())).thenThrow(new IllegalStateException("A down"));
        when(gatewayB.charge(anyString(), any())).thenThrow(new IllegalStateException("B down"));
        meetInSameContext("checkout", 3);

        assertThat(checkout.pay("o2", BigDecimal.ONE)).isEqualTo("C");

        verify(gatewayC).charge("o2", BigDecimal.ONE);
        verify(documentDb).save("o2", "C");
    }

    @Test
    void firstGatewayWorksOthersUntouched() {
        meetInSameContext("checkout", 3);

        assertThat(checkout.pay("o3", BigDecimal.ZERO)).isEqualTo("A");

        verify(gatewayA).charge("o3", BigDecimal.ZERO);
        verify(gatewayB, never()).charge(anyString(), any());
        verifyNoInteractions(gatewayC);
        verify(documentDb).save("o3", "A");
    }
}
