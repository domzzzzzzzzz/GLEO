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

    @Query("select coalesce(max(m.categoryOrder),0) from MenuItem m where m.vendor = :vendor")
    Integer findMaxCategoryOrder(@Param("vendor") Vendor vendor);

    @Query("select min(m.categoryOrder) from MenuItem m where m.vendor = :vendor and ((:category is null and (m.category is null or m.category = '')) or m.category = :category)")
    Integer findCategoryOrderForCategory(@Param("vendor") Vendor vendor, @Param("category") String category);

    @Query("select coalesce(max(m.itemOrder),0) from MenuItem m where m.vendor = :vendor and ((:category is null and (m.category is null or m.category = '')) or m.category = :category)")
    Integer findMaxItemOrderForCategory(@Param("vendor") Vendor vendor, @Param("category") String category);

    MenuItem findFirstByVendorAndImagePathIsNotNullOrderByItemOrderAscIdAsc(Vendor vendor);
}
