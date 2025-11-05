package com.fbcorp.gleo.repository;

import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.model.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    @Modifying
    @Transactional
    @Query("UPDATE Vendor v SET v.status = :status WHERE v.id = :vendorId")
    void updateStatus(Long vendorId, VendorStatus status);
}
