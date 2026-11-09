package demo.project;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Dùng ba cổng thanh toán cùng kiểu theo thứ tự A, B, C: gateway lỗi thì thử cổng kế tiếp. */
@Service
public class CheckoutService {

    private final PaymentGateway gatewayA;
    private final PaymentGateway gatewayB;
    private final PaymentGateway gatewayC;
    private final Ports.Mailer mailer;
    private final Ports.DocumentDb documentDb;

    public CheckoutService(@Qualifier("gatewayA") PaymentGateway gatewayA, @Qualifier("gatewayB") PaymentGateway gatewayB,
                           @Qualifier("gatewayC") PaymentGateway gatewayC, Ports.Mailer mailer, Ports.DocumentDb documentDb) {
        this.gatewayA = gatewayA;
        this.gatewayB = gatewayB;
        this.gatewayC = gatewayC;
        this.mailer = mailer;
        this.documentDb = documentDb;
    }

    /** Trả về tên cổng đã thử thành công. */
    public String pay(String order, BigDecimal amount) {
        String used;
        try {
            gatewayA.charge(order, amount);
            used = "A";
        } catch (RuntimeException a) {
            try {
                gatewayB.charge(order, amount);
                used = "B";
            } catch (RuntimeException b) {
                gatewayC.charge(order, amount);
                used = "C";
            }
        }
        documentDb.save(order, used);
        mailer.send(order);
        return used;
    }
}
