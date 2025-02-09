package com.ike.balanceservice.repository;

import com.ike.balanceservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}