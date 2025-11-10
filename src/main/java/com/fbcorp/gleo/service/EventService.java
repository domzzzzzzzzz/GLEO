package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.*;
import com.fbcorp.gleo.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class EventService {
    private final EventRepo eventRepo;
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final OrderRepo orderRepo;
    private final TicketRepo ticketRepo;
    private final TierPolicyRepo tierPolicyRepo;
    private final TierConsumptionRepo tierConsumptionRepo;
    private final UserAccountRepo userAccountRepo;
    private final TicketImportLogRepo ticketImportLogRepo;

    public EventService(EventRepo eventRepo, 
                       VendorRepo vendorRepo,
                       MenuItemRepo menuItemRepo,
                       OrderRepo orderRepo,
                       TicketRepo ticketRepo, 
                       TierPolicyRepo tierPolicyRepo,
                       TierConsumptionRepo tierConsumptionRepo,
                       UserAccountRepo userAccountRepo,
                       TicketImportLogRepo ticketImportLogRepo) {
        this.eventRepo = eventRepo;
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.orderRepo = orderRepo;
        this.ticketRepo = ticketRepo;
        this.tierPolicyRepo = tierPolicyRepo;
        this.tierConsumptionRepo = tierConsumptionRepo;
        this.userAccountRepo = userAccountRepo;
        this.ticketImportLogRepo = ticketImportLogRepo;
    }

    public Event getByCode(String code) {
        return eventRepo.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    @Transactional
    public String delete(String eventCode) {
        /**
         * Deletes an event and all associated data while respecting foreign key constraints.
         * Deletion order matters because menu_items has a FK to vendors and vendors has a FK to events.
         * Steps:
         * 1. Tier consumption rows
         * 2. Orders (cascades order items)
         * 3. Menu items per vendor then vendors (avoids FK violation menu_items.vendor_id)
         * 4. Tickets
         * 5. Ticket import logs
         * 6. Tier policies
         * 7. Null out user account links (vendor + event)
         * 8. Delete the event itself
         */
        try {
            Event event = getByCode(eventCode);
            String eventName = event.getName(); // Store name before deletion

            // 1. Delete all tier consumptions for this event
            tierConsumptionRepo.deleteAll(tierConsumptionRepo.findByEvent(event));

            // 2. Delete all orders for this event (will cascade delete order items)
            orderRepo.deleteAll(orderRepo.findByEvent(event));

            // 3. Delete all menu items per vendor then delete vendors (explicit to avoid FK violation)
            var vendorsForEvent = vendorRepo.findByEvent(event);
            vendorsForEvent.forEach(vendor -> {
                // Remove menu items first to satisfy FK constraint menu_items.vendor_id -> vendors.id
                menuItemRepo.deleteByVendor(vendor);
                // Clear vendor reference from user accounts referencing this vendor
                userAccountRepo.findByVendor(vendor).forEach(ua -> ua.setVendor(null));
            });
            userAccountRepo.flush(); // Ensure null vendor updates are persisted before vendor delete
            vendorRepo.deleteAll(vendorsForEvent);

            // 4. Delete all tickets for this event
            ticketRepo.deleteAll(ticketRepo.findByEvent(event));

            // 5. Delete all import logs for this event
            ticketImportLogRepo.deleteAll(ticketImportLogRepo.findByEvent(event));

            // 6. Delete all tier policies for this event
            tierPolicyRepo.deleteAll(tierPolicyRepo.findByEvent(event));

            // 7. Clear event reference from user accounts in a single batch update
            userAccountRepo.findByEvent(event).forEach(ua -> {
                ua.setEvent(null);
            });
            userAccountRepo.flush(); // Flush the changes

            // 8. Finally delete the event
            eventRepo.delete(event);
            
            return eventName; // Return the name for display
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to delete event: " + e.getMessage(), e);
        }
    }
}