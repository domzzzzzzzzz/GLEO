package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepo extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findByVendor(com.fbcorp.gleo.domain.Vendor vendor);
}
