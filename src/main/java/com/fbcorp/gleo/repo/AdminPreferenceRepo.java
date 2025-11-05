package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.AdminPreference;
import com.fbcorp.gleo.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminPreferenceRepo extends JpaRepository<AdminPreference, Long> {
    Optional<AdminPreference> findByUser(UserAccount user);
    Optional<AdminPreference> findByUser_Username(String username);
}
