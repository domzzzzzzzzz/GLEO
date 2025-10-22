package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepo extends JpaRepository<Order, Long> {

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Order o "
         + "WHERE o.ticket.id = :ticketId "
         + "AND o.vendor.id = :vendorId "
         + "AND o.status IN (com.fbcorp.gleo.domain.OrderStatus.NEW, com.fbcorp.gleo.domain.OrderStatus.PREPARING, com.fbcorp.gleo.domain.OrderStatus.READY)")
    boolean existsOpenOrder(@Param("ticketId") Long ticketId, @Param("vendorId") Long vendorId);

    java.util.List<Order> findByEvent(Event event);

    java.util.List<Order> findByVendor(Vendor vendor);

    java.util.List<Order> findByTicketOrderByCreatedAtDesc(Ticket ticket);
}
