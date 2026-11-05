package com.example.parallel.account;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Account " + id + " not found");
    }
}
