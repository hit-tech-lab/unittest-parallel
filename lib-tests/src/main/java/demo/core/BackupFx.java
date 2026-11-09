package demo.core;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component("backupFx")
public class BackupFx implements ExchangeRateClient {
    @Override
    public BigDecimal rate(String from, String to) {
        return new BigDecimal("1.20");
    }
}
