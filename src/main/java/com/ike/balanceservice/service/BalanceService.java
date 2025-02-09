package com.ike.balanceservice.service;

import com.ike.balanceservice.entity.Account;
import com.ike.balanceservice.entity.Transaction;
import com.ike.balanceservice.exception.AccountNotFoundException;
import com.ike.balanceservice.exception.InsufficientBalanceException;
import com.ike.balanceservice.repository.AccountRepository;
import com.ike.balanceservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "accountBalanceCache")
public class BalanceService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RedissonClient redissonClient;

    @Transactional
    @CachePut(key = "#destinationAccountNumber") // 更新目标账户缓存
    @CacheEvict(key = "#sourceAccountNumber")   // 清除源账户缓存
    public Account processTransaction(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount) {
        RLock lock = redissonClient.getLock("account-lock-" + sourceAccountNumber); // 分布式锁
        try {
            lock.lock(); // 获取锁
            Account sourceAccount = getAccountWithCache(sourceAccountNumber);
            Account destinationAccount = getAccountWithCache(destinationAccountNumber);

            if (sourceAccount == null || destinationAccount == null) {
                throw new AccountNotFoundException("Account not found");
            }
            if (sourceAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException("Insufficient balance");
            }

            sourceAccount.debit(amount);
            destinationAccount.credit(amount);

            accountRepository.save(sourceAccount);
            accountRepository.save(destinationAccount);

            Transaction transaction = new Transaction();
            transaction.setTransactionId(UUID.randomUUID().toString());
            transaction.setSourceAccountNumber(sourceAccountNumber);
            transaction.setDestinationAccountNumber(destinationAccountNumber);
            transaction.setAmount(amount);
            transaction.setTimestamp(LocalDateTime.now());
            transactionRepository.save(transaction);

            return destinationAccount; // 返回目标账户，也可以根据需要返回其他信息
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock(); // 释放锁
            }
        }
    }


    @Cacheable(key = "#accountNumber") // 缓存账户余额
    public Account getAccountWithCache(String accountNumber) {
        return accountRepository.findById(accountNumber).orElse(null);
    }

    public Account createAccount(String accountNumber, BigDecimal initialBalance) {
        Account account = new Account(accountNumber, initialBalance);
        return accountRepository.save(account);
    }

    public Account getAccount(String accountNumber) {
        return accountRepository.findById(accountNumber).orElseThrow(() -> new AccountNotFoundException("Account not found"));
    }

    @CacheEvict(key = "#accountNumber") // 清除缓存
    public void evictAccountCache(String accountNumber) {
        // 清除缓存的操作由 @CacheEvict 注解自动完成
    }
}