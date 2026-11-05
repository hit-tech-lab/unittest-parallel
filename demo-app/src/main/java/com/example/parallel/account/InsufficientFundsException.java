package com.example.parallel.account;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long accountId, BigDecimal balance, BigDecimal requested) {
        super("Account " + accountId + " has " + balance + ", requested " + requested);
    }
}
