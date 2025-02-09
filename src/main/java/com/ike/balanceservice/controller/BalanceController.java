package com.ike.balanceservice.controller;

import com.ike.balanceservice.controller.dto.TransactionRequest;
import com.ike.balanceservice.entity.Account;
import com.ike.balanceservice.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @PostMapping("/transactions")
    public ResponseEntity<Account> processTransaction(@RequestBody TransactionRequest transactionRequest) {
        Account updatedAccount = balanceService.processTransaction(
                transactionRequest.getSourceAccountNumber(),
                transactionRequest.getDestinationAccountNumber(),
                transactionRequest.getAmount()
        );
        return ResponseEntity.ok(updatedAccount);
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getAccountBalance(@PathVariable String accountNumber) {
        Account account = balanceService.getAccount(accountNumber);
        return ResponseEntity.ok(account.getBalance());
    }

    @PostMapping
    public ResponseEntity<Account> createAccount(@RequestParam String accountNumber, @RequestParam BigDecimal initialBalance) {
        Account account = balanceService.createAccount(accountNumber, initialBalance);
        return new ResponseEntity<>(account, HttpStatus.CREATED);
    }
}