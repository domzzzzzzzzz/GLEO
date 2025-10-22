package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventRepo extends JpaRepository<Event, Long> {
    Optional<Event> findByCode(String code);
}
