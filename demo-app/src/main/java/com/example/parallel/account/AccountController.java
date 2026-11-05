package com.example.parallel.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository repository;

    public AccountController(AccountRepository repository) {
        this.repository = repository;
    }

    public record CreateAccountRequest(
            @NotBlank String owner,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull @DecimalMin("0") BigDecimal balance) {
    }

    public record AccountResponse(Long id, String owner, String currency, BigDecimal balance) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getOwner(), a.getCurrency(), a.getBalance());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(repository.save(
                new Account(request.owner(), request.currency(), request.balance())));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return repository.findById(id).map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(AccountNotFoundException e) {
        return e.getMessage();
    }
}
