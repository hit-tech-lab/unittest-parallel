package com.example.parallel.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.parallel.account.Account;
import com.example.parallel.account.InsufficientFundsException;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountTest {

    @ParameterizedTest
    @CsvSource({"100,30,70", "100,100,0", "0.5,0.25,0.25"})
    void debit(String balance, String amount, String expected) {
        Account a = new Account("x", "USD", new BigDecimal(balance));
        a.debit(new BigDecimal(amount));
        assertThat(a.getBalance()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0,0.01", "10,10.0001"})
    void debitMoreThanBalanceFails(String balance, String amount) {
        Account a = new Account("x", "USD", new BigDecimal(balance));
        assertThatThrownBy(() -> a.debit(new BigDecimal(amount))).isInstanceOf(InsufficientFundsException.class);
        assertThat(a.getBalance()).isEqualByComparingTo(balance);
    }
}
