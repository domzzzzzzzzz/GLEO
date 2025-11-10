package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.EventRepo;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringBootTest
class EventServiceDeletionTest {

    @Autowired
    EventRepo eventRepo;
    @Autowired
    VendorRepo vendorRepo;
    @Autowired
    MenuItemRepo menuItemRepo;
    @Autowired
    EventService eventService;

    @Test
    @Transactional
    void deleteEventRemovesVendorsAndMenuItems() {
        // Arrange: create event
        Event event = new Event();
        event.setCode("TDEL" + System.currentTimeMillis());
        event.setName("Temp Delete Test");
        event.setStartAt(LocalDateTime.now());
        event.setEndAt(LocalDateTime.now().plusHours(1));
        eventRepo.save(event);

        // Create vendor
        Vendor vendor = new Vendor();
        vendor.setEvent(event);
        vendor.setName("VendorA");
        vendorRepo.save(vendor);

        // Create menu item
        MenuItem mi = new MenuItem();
        mi.setVendor(vendor);
        mi.setName("Burger");
        mi.setPrice(new BigDecimal("10.00"));
        mi.setAvailable(true);
        menuItemRepo.save(mi);

        Long eventId = event.getId();
        Long vendorId = vendor.getId();
        Long menuItemId = mi.getId();

        // Act
        String deletedName = eventService.delete(event.getCode());

        // Assert
        Assertions.assertEquals("Temp Delete Test", deletedName);
        Assertions.assertTrue(eventRepo.findById(eventId).isEmpty(), "Event should be deleted");
        Assertions.assertTrue(vendorRepo.findById(vendorId).isEmpty(), "Vendor should be deleted");
        Assertions.assertTrue(menuItemRepo.findById(menuItemId).isEmpty(), "MenuItem should be deleted");
    }
}
