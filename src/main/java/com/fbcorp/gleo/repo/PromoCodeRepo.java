package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromoCodeRepo extends JpaRepository<PromoCode, Long> {
    List<PromoCode> findByEventOrderByCodeAsc(Event event);

    Optional<PromoCode> findByEventAndCodeIgnoreCase(Event event, String code);

    Optional<PromoCode> findByEventAndCodeIgnoreCaseAndActiveTrue(Event event, String code);

    boolean existsByEventAndCodeIgnoreCase(Event event, String code);
}
