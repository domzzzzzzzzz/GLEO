package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepo extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByVendorAndAvailableTrue(Vendor vendor);

    List<MenuItem> findByVendorOrderByNameAsc(Vendor vendor);

    List<MenuItem> findByVendorAndNameIgnoreCase(Vendor vendor, String name);

    void deleteByVendor(Vendor vendor);
    
    @Query("SELECT DISTINCT m.category FROM MenuItem m WHERE m.vendor.event = :event AND m.category IS NOT NULL AND m.category != ''")
    List<String> findDistinctCategoriesByEvent(@Param("event") Event event);
}
