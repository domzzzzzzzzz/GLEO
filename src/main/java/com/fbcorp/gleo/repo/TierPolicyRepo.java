package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.TierCode;
import com.fbcorp.gleo.domain.TierPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TierPolicyRepo extends JpaRepository<TierPolicy, Long> {
    Optional<TierPolicy> findByEventAndTierCode(Event event, TierCode tierCode);
    List<TierPolicy> findByEvent(Event event);
}
