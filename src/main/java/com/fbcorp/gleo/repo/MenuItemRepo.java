package com.fbcorp.gleo.repo;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepo extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByVendorAndAvailableTrue(Vendor vendor);

    List<MenuItem> findByVendorOrderByNameAsc(Vendor vendor);

    java.util.Optional<MenuItem> findByVendorAndNameIgnoreCase(Vendor vendor, String name);

    void deleteByVendor(Vendor vendor);
}
