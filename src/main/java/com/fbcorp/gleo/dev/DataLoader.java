package com.fbcorp.gleo.dev;

import com.fbcorp.gleo.domain.*;
import com.fbcorp.gleo.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class DataLoader implements CommandLineRunner {

    private final EventRepo eventRepo;
    private final VendorRepo vendorRepo;
    private final MenuItemRepo menuItemRepo;
    private final TicketRepo ticketRepo;
    private final TierPolicyRepo tierPolicyRepo;
    private final RoleRepo roleRepo;
    private final UserAccountRepo userAccountRepo;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public DataLoader(EventRepo eventRepo, VendorRepo vendorRepo,
                      MenuItemRepo menuItemRepo, TicketRepo ticketRepo,
                      TierPolicyRepo tierPolicyRepo,
                      RoleRepo roleRepo, UserAccountRepo userAccountRepo,
                      org.springframework.security.crypto.password.PasswordEncoder passwordEncoder){
        this.eventRepo = eventRepo;
        this.vendorRepo = vendorRepo;
        this.menuItemRepo = menuItemRepo;
        this.ticketRepo = ticketRepo;
        this.tierPolicyRepo = tierPolicyRepo;
        this.roleRepo = roleRepo;
        this.userAccountRepo = userAccountRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Event event = eventRepo.findByCode("G2025").orElseGet(() -> {
            Event created = new Event();
            created.setCode("G2025");
            created.setName("GLEO Demo Event");
            created.setStartAt(LocalDateTime.now().minusHours(1));
            created.setEndAt(LocalDateTime.now().plusHours(6));
            return eventRepo.save(created);
        });

        Vendor v1 = ensureVendor(event, "BRGR", "1234", "/images/brgr.png");
        Vendor v2 = ensureVendor(event, "DESOUKY&SODA", "4321", "/images/desoky-soda.png");
        Vendor v3 = ensureVendor(event, "Koffee Kulture", "9876", "/images/koffee-kulture.jpg");

        ensureTierPolicy(event, TierCode.VIP, true, null);
        ensureTierPolicy(event, TierCode.REG, false, 1);

        ensureMenuItem(v1, "Smash BRGR");
        ensureMenuItem(v1, "Truffle Fries");
        ensureMenuItem(v1, "Loaded Chicken Strips");

        ensureMenuItem(v2, "Desouky Street Pizza");
        ensureMenuItem(v2, "Creamy Macarona Bechamel");
        ensureMenuItem(v2, "Desouky Liver Sandwich");

        ensureMenuItem(v3, "Signature Cappuccino");
        ensureMenuItem(v3, "Cold Brew Tonic");
        ensureMenuItem(v3, "Hazelnut Latte");

        ensureTicket(event, "VIP-001", TierCode.VIP, "VIP Guest", "01000000001", "S-VIP-1");
        ensureTicket(event, "REG-001", TierCode.REG, "REG Guest", "01000000002", "S-REG-1");

        Role adminRole = ensureRole("ROLE_ADMIN");
        Role organizerRole = ensureRole("ROLE_ORGANIZER");
        Role vendorRole = ensureRole("ROLE_VENDOR");
        Role staffRole = ensureRole("ROLE_STAFF");
        Role usherRole = ensureRole("ROLE_USHER");

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
        ensureUsherAccount("usher_desouky", v2, usherRole);
        ensureUsherAccount("usher_koffee", v3, usherRole);
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

    private void ensureMenuItem(Vendor vendor, String name) {
        menuItemRepo.findByVendorAndNameIgnoreCase(vendor, name)
                .map(existing -> updateMenuItem(existing, vendor, name))
                .orElseGet(() -> {
                    MenuItem item = new MenuItem();
                    item.setVendor(vendor);
                    item.setName(name);
                    item.setPrice(BigDecimal.ZERO);
                    item.setAvailable(true);
                    return menuItemRepo.save(item);
                });
    }

    private MenuItem updateMenuItem(MenuItem item, Vendor vendor, String name) {
        item.setVendor(vendor);
        item.setName(name);
        if (item.getPrice() == null) {
            item.setPrice(BigDecimal.ZERO);
        }
        item.setAvailable(true);
        return menuItemRepo.save(item);
    }

    private void ensureTierPolicy(Event event, TierCode tierCode, boolean unlimited, Integer maxItemsPerVendor) {
        TierPolicy policy = tierPolicyRepo.findByEventAndTierCode(event, tierCode).orElseGet(() -> {
            TierPolicy tp = new TierPolicy();
            tp.setEvent(event);
            tp.setTierCode(tierCode);
            return tp;
        });
        policy.setUnlimited(unlimited);
        policy.setMaxItemsPerVendor(unlimited ? null : maxItemsPerVendor);
        tierPolicyRepo.save(policy);
    }

    private void ensureTicket(Event event, String qrCode, TierCode tierCode, String holderName, String holderPhone, String serial) {
        Ticket ticket = ticketRepo.findByQrCode(qrCode).orElseGet(() -> {
            Ticket t = new Ticket();
            t.setQrCode(qrCode);
            t.setActive(true);
            return t;
        });
        ticket.setEvent(event);
        ticket.setTierCode(tierCode);
        ticket.setHolderName(holderName);
        ticket.setHolderPhone(holderPhone);
        ticket.setSerial(serial);
        ticket.setActive(true);
        ticketRepo.save(ticket);
    }

    private UserAccount ensureUser(String username, String rawPassword) {
        UserAccount user = userAccountRepo.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            return u;
        });
        user.setPassword(passwordEncoder.encode(rawPassword));
        return user;
    }

    private void addRole(UserAccount user, Role role) {
        if (user.getRoles().stream().noneMatch(existing -> existing.getName().equalsIgnoreCase(role.getName()))) {
            user.getRoles().add(role);
        }
    }
}
