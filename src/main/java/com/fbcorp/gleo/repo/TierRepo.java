package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Tier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TierRepo extends JpaRepository<Tier, Long> {
    Optional<Tier> findByEventAndCodeIgnoreCase(Event event, String code);
    List<Tier> findByEventOrderByDisplayOrderAscNameAsc(Event event);
}
