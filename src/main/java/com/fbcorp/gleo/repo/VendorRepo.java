package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorRepo extends JpaRepository<Vendor, Long> {
    List<Vendor> findByEventAndActiveTrue(Event event);
}
