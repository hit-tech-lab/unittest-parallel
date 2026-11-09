package demo.core;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("account " + id);
    }
}
