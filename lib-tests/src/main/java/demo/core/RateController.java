package demo.core;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateController {

    private final ExchangeRateClient primary;
    private final ExchangeRateClient backup;

    public RateController(@Qualifier("primaryFx") ExchangeRateClient primary, @Qualifier("backupFx") ExchangeRateClient backup) {
        this.primary = primary;
        this.backup = backup;
    }

    @GetMapping("/rates/{from}/{to}")
    public ResponseEntity<BigDecimal> rate(@PathVariable String from, @PathVariable String to,
                                           @RequestParam(defaultValue = "primary") String via) {
        BigDecimal rate = "backup".equals(via) ? backup.rate(from, to) : primary.rate(from, to);
        return ResponseEntity.ok().header("X-Thread", Thread.currentThread().getName()).body(rate);
    }
}
