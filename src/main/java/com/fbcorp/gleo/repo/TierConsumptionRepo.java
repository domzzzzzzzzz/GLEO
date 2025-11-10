package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TierConsumptionRepo extends JpaRepository<TierConsumption, Long> {

    Optional<TierConsumption> findByEventAndTicketAndVendor(Event event, Ticket ticket, Vendor vendor);

    @Query("SELECT COALESCE(tc.totalItemsConsumed,0) FROM TierConsumption tc WHERE tc.event = :event AND tc.ticket = :ticket AND tc.vendor = :vendor")
    Integer consumedCount(@Param("event") Event event,
                          @Param("ticket") Ticket ticket,
                          @Param("vendor") Vendor vendor);

    List<TierConsumption> findByEvent(Event event);
}
