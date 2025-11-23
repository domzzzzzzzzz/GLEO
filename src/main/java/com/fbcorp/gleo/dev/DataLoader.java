package com.fbcorp.gleo.dev;

import com.fbcorp.gleo.domain.*;
import com.fbcorp.gleo.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.fbcorp.gleo.service.TierService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataLoader implements CommandLineRunner {

    private final EventRepo eventRepo;
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final TicketRepo ticketRepo;
    private final TierPolicyRepo tierPolicyRepo;
    private final TierService tierService;
    private final RoleRepo roleRepo;
    private final UserAccountRepo userAccountRepo;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final boolean seedDemoTickets;
    private final boolean seedDemoData;

    public DataLoader(EventRepo eventRepo, VendorRepo vendorRepo,
                      MenuItemRepo menuItemRepo, TicketRepo ticketRepo,
                      TierPolicyRepo tierPolicyRepo, TierService tierService,
                      RoleRepo roleRepo, UserAccountRepo userAccountRepo,
                      org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                      @Value("${gleo.seed-demo-tickets:false}") boolean seedDemoTickets,
                      @Value("${gleo.seed-demo-data:false}") boolean seedDemoData){
        this.eventRepo = eventRepo;
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.ticketRepo = ticketRepo;
        this.tierPolicyRepo = tierPolicyRepo;
        this.tierService = tierService;
        this.roleRepo = roleRepo;
        this.userAccountRepo = userAccountRepo;
        this.passwordEncoder = passwordEncoder;
        this.seedDemoTickets = seedDemoTickets;
        this.seedDemoData = seedDemoData;
    }

    @Override
    public void run(String... args) {
        // All demo seeding is disabled by default for production safety.
        if (!seedDemoData && !seedDemoTickets) {
            return;
        }

        var existingEvent = eventRepo.findByCode("G2025");
        boolean isNewEvent = existingEvent.isEmpty();
        Event event = existingEvent.orElseGet(() -> {
            Event created = new Event();
            created.setCode("G2025");
            created.setName("GLEO Demo Event");
            created.setStartAt(LocalDateTime.now().minusHours(1));
            created.setEndAt(LocalDateTime.now().plusHours(6));
            return eventRepo.save(created);
        });
        Tier vipTier = tierService.getOrCreate(event, "VIP", "VIP", 0);
        Tier regTier = tierService.getOrCreate(event, "REG", "Regular", 1);

        Vendor v1 = vendorRepo.findByEventAndNameIgnoreCase(event, "BRGR").orElse(null);
        Vendor v2 = vendorRepo.findByEventAndNameIgnoreCase(event, "DESOUKY&SODA").orElse(null);
        Vendor v3 = vendorRepo.findByEventAndNameIgnoreCase(event, "Koffee Kulture").orElse(null);

        if (seedDemoData && isNewEvent) {
            v1 = ensureVendor(event, "BRGR", "1234", "/images/brgr.png");
            v2 = ensureVendor(event, "DESOUKY&SODA", "4321", "/images/desoky-soda.png");
            v3 = ensureVendor(event, "Koffee Kulture", "9876", "/images/koffee-kulture.jpg");

            ensureMenuItem(v1, "Smash BRGR", "Burgers");
            ensureMenuItem(v1, "Truffle Fries", "Sides");

            ensureMenuItem(v2, "Desouky Street Pizza", "Burgers");
            ensureMenuItem(v2, "Creamy Macarona Bechamel", "Sides");
            ensureMenuItem(v2, "Desouky Liver Sandwich", "Burgers");

            ensureMenuItem(v3, "Signature Cappuccino", "Drinks");
            ensureMenuItem(v3, "Cold Brew Tonic", "Drinks");
            ensureMenuItem(v3, "Hazelnut Latte", "Drinks");

            ensureTierPolicy(event, vipTier, TierCode.VIP, true, null);
            ensureTierPolicy(event, regTier, TierCode.REG, false, 1);
        }

        if (seedDemoTickets) {
            ensureTierPolicy(event, vipTier, TierCode.VIP, true, null);
            ensureTierPolicy(event, regTier, TierCode.REG, false, 1);
            ensureTicket(event, vipTier, TierCode.VIP, "VIP-001", "VIP Guest", "01000000001", "S-VIP-1");
            ensureTicket(event, regTier, TierCode.REG, "REG-001", "REG Guest", "01000000002", "S-REG-1");
        }

        if (seedDemoData) {
            Role adminRole = ensureRole("ADMIN");
            Role organizerRole = ensureRole("ORGANIZER");
            Role vendorRole = ensureRole("VENDOR");
            Role staffRole = ensureRole("STAFF");
            Role usherRole = ensureRole("USHER");

            UserAccount admin = ensureUser("admin", "Admin@123");
            addRole(admin, adminRole);
            addRole(admin, organizerRole);
            admin.setEvent(null);
            admin.setVendor(null);
            userAccountRepo.save(admin);

            UserAccount organizer = ensureUser("organizer", "Organizer@123");
            addRole(organizer, organizerRole);
            organizer.setEvent(event);
            organizer.setVendor(null);
            userAccountRepo.save(organizer);

            if (v1 != null) {
                UserAccount vendorUser = ensureUser("vendor1", "Vendor@123");
                addRole(vendorUser, vendorRole);
                vendorUser.setVendor(v1);
                vendorUser.setEvent(event);
                userAccountRepo.save(vendorUser);

                UserAccount staff = ensureUser("staff1", "Staff@123");
                addRole(staff, staffRole);
                staff.setVendor(v1);
                staff.setEvent(event);
                userAccountRepo.save(staff);

                ensureUsherAccount("usher_brgr", v1, usherRole);
            }
            if (v2 != null) {
                ensureUsherAccount("usher_desouky", v2, usherRole);
            }
            if (v3 != null) {
                ensureUsherAccount("usher_koffee", v3, usherRole);
            }
        }
    }

    private Role ensureRole(String name){
        return roleRepo.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            return roleRepo.save(role);
        });
    }

    private void ensureUsherAccount(String username, Vendor vendor, Role usherRole) {
        UserAccount usher = ensureUser(username, "Usher@123");
        addRole(usher, usherRole);
        usher.setVendor(vendor);
        usher.setEvent(vendor.getEvent());
        userAccountRepo.save(usher);
    }

    private Vendor ensureVendor(Event event, String name, String pin, String imagePath) {
        return vendorRepo.findByEventAndNameIgnoreCase(event, name)
                .map(existing -> updateVendor(existing, event, name, pin, imagePath))
                .orElseGet(() -> {
                    Vendor vendor = new Vendor();
                    vendor.setEvent(event);
                    vendor.setName(name);
                    vendor.setPinPlain(pin);
                    vendor.setImagePath(imagePath);
                    vendor.setActive(true);
                    return vendorRepo.save(vendor);
                });
    }

    private Vendor updateVendor(Vendor vendor, Event event, String name, String pin, String imagePath) {
        vendor.setEvent(event);
        vendor.setName(name);
        vendor.setPinPlain(pin);
        vendor.setImagePath(imagePath);
        vendor.setActive(true);
        return vendorRepo.save(vendor);
    }

    private void ensureMenuItem(Vendor vendor, String name, String category) {
        var existingItems = menuItemRepo.findByVendorAndNameIgnoreCase(vendor, name);
        if (existingItems.isEmpty()) {
            MenuItem item = new MenuItem();
            item.setVendor(vendor);
            item.setName(name);
            item.setCategory(category);
            item.setPrice(BigDecimal.ZERO);
            item.setAvailable(true);
            item.setStockLevel(200); // default demo stock
            item.setLowStockThreshold(25);
            menuItemRepo.save(item);
            return;
        }
        MenuItem primary = existingItems.get(0);
        updateMenuItem(primary, vendor, name, category);
    }

    private MenuItem updateMenuItem(MenuItem item, Vendor vendor, String name, String category) {
        item.setVendor(vendor);
        item.setName(name);
        item.setCategory(category);
        if (item.getPrice() == null) {
            item.setPrice(BigDecimal.ZERO);
        }
        item.setAvailable(true);
        if (item.getStockLevel() == null) {
            item.setStockLevel(200);
        }
        return menuItemRepo.save(item);
    }

    private void ensureTierPolicy(Event event, Tier tier, TierCode tierCode, boolean unlimited, Integer maxItemsPerVendor) {
        tierPolicyRepo.findByEventAndTierCode(event, tierCode).ifPresentOrElse(existing -> {
            // Keep whatever restrictions the organizer configured previously; only ensure it belongs to this event.
            if (!event.equals(existing.getEvent())) {
                existing.setEvent(event);
                tierPolicyRepo.save(existing);
            }
            if (existing.getTier() == null) {
                existing.setTier(tier);
                existing.setTierCode(tierCode);
                tierPolicyRepo.save(existing);
            }
        }, () -> {
            TierPolicy policy = new TierPolicy();
            policy.setEvent(event);
            policy.setTierCode(tierCode);
            policy.setTier(tier);
            policy.setUnlimited(unlimited);
            policy.setMaxItemsPerVendor(unlimited ? null : maxItemsPerVendor);
            if (tierCode == TierCode.REG) {
                policy.setOneVendorOnly(true);
            }
            tierPolicyRepo.save(policy);
        });
    }

    private void ensureTicket(Event event, Tier tier, TierCode tierCode, String qrCode, String holderName, String holderPhone, String serial) {
        Ticket ticket = ticketRepo.findByQrCode(qrCode).orElseGet(() -> {
            Ticket t = new Ticket();
            t.setQrCode(qrCode);
            t.setActive(true);
            return t;
        });
        
        // Only update basic fields, preserve boundDeviceHash if already set
        ticket.setEvent(event);
        ticket.setTierCode(tierCode);
        ticket.setTier(tier);
        ticket.setHolderName(holderName);
        ticket.setHolderPhone(holderPhone);
        ticket.setSerial(serial);
        ticket.setActive(true);
        // DO NOT reset boundDeviceHash - it should persist across restarts
        
        ticketRepo.save(ticket);
    }

    private UserAccount ensureUser(String username, String rawPassword) {
        UserAccount user = userAccountRepo.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPassword(passwordEncoder.encode(rawPassword));
            return u;
        });
        // Don't re-encode password on every startup - only set if user is new
        return user;
    }

    private void addRole(UserAccount user, Role role) {
        if (user.getRoles().stream().noneMatch(existing -> existing.getName().equalsIgnoreCase(role.getName()))) {
            user.getRoles().add(role);
        }
    }
}
