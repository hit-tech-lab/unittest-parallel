package com.example.parallel.transfer;

import com.example.parallel.account.InsufficientFundsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    public record TransferRequest(@NotNull Long fromAccountId, @NotNull Long toAccountId, @NotNull BigDecimal amount) {
    }

    @PostMapping("/transfers")
    public TransferService.TransferResult transfer(@Valid @RequestBody TransferRequest request) {
        return transferService.transfer(request.fromAccountId(), request.toAccountId(), request.amount());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String insufficientFunds(InsufficientFundsException e) {
        return e.getMessage();
    }
}
