package com.ike.balanceservice.service;

import com.ike.balanceservice.entity.Account;
import com.ike.balanceservice.exception.AccountNotFoundException;
import com.ike.balanceservice.exception.InsufficientBalanceException;
import com.ike.balanceservice.repository.AccountRepository;
import com.ike.balanceservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BalanceServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RedissonClient redissonClient; // Mock RedissonClient

    @InjectMocks
    private BalanceService balanceService;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = new Account("ACCT001", BigDecimal.valueOf(100));
        destinationAccount = new Account("ACCT002", BigDecimal.valueOf(50));
    }

    @Test
    void processTransaction_success() {
        BigDecimal transferAmount = BigDecimal.valueOf(30);

        when(accountRepository.findById("ACCT001")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById("ACCT002")).thenReturn(Optional.of(destinationAccount));
        when(redissonClient.getLock(anyString())).thenReturn(mock(org.redisson.api.RLock.class)); // Mock Lock

        Account updatedDestinationAccount = balanceService.processTransaction("ACCT001", "ACCT002", transferAmount);

        assertNotNull(updatedDestinationAccount);
        assertEquals(BigDecimal.valueOf(80), sourceAccount.getBalance()); // 源账户余额减少
        assertEquals(BigDecimal.valueOf(80), destinationAccount.getBalance()); // 目标账户余额增加 (50 + 30)
        verify(accountRepository, times(2)).save(any(Account.class)); // 账户保存被调用两次
        verify(transactionRepository, times(1)).save(any()); // 交易记录保存被调用一次
    }

    @Test
    void processTransaction_insufficientBalance() {
        BigDecimal transferAmount = BigDecimal.valueOf(150); // 大于源账户余额

        when(accountRepository.findById("ACCT001")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById("ACCT002")).thenReturn(Optional.of(destinationAccount));
        when(redissonClient.getLock(anyString())).thenReturn(mock(org.redisson.api.RLock.class)); // Mock Lock

        assertThrows(InsufficientBalanceException.class, () -> {
            balanceService.processTransaction("ACCT001", "ACCT002", transferAmount);
        });

        assertEquals(BigDecimal.valueOf(100), sourceAccount.getBalance()); // 余额不应改变
        assertEquals(BigDecimal.valueOf(50), destinationAccount.getBalance()); // 余额不应改变
        verify(accountRepository, never()).save(any(Account.class)); // 账户不应该被保存
        verify(transactionRepository, never()).save(any()); // 交易记录不应该被保存
    }

    @Test
    void processTransaction_accountNotFound() {
        BigDecimal transferAmount = BigDecimal.valueOf(20);

        when(accountRepository.findById("ACCT001")).thenReturn(Optional.empty()); // 源账户不存在

        assertThrows(AccountNotFoundException.class, () -> {
            balanceService.processTransaction("ACCT001", "ACCT002", transferAmount);
        });

        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getAccountWithCache_cacheHit() {
        String accountNumber = "ACCT001";
        when(accountRepository.findById(accountNumber)).thenReturn(Optional.of(sourceAccount));

        // 第一次调用，应该从数据库读取并放入缓存
        balanceService.getAccountWithCache(accountNumber);
        verify(accountRepository, times(1)).findById(accountNumber);

        // 第二次调用，应该从缓存读取，不应该再调用数据库
        balanceService.getAccountWithCache(accountNumber);
        verify(accountRepository, times(1)).findById(accountNumber); // 仍然只调用一次
    }

    @Test
    void getAccountWithCache_cacheMiss() {
        String accountNumber = "ACCT003"; // 假设缓存中没有这个账户，数据库也没有

        when(accountRepository.findById(accountNumber)).thenReturn(Optional.empty());

        Account account = balanceService.getAccountWithCache(accountNumber);

        assertNull(account); // 应该返回 null，因为账户不存在
        verify(accountRepository, times(1)).findById(accountNumber); // 数据库应该被调用一次
    }
}