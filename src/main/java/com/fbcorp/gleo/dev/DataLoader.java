package com.fbcorp.gleo.dev;

import com.fbcorp.gleo.domain.*;
import com.fbcorp.gleo.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    public void run(String... args) throws Exception {
        if (eventRepo.count() > 0) return;

        Event e = new Event();
        e.setCode("G2025");
        e.setName("GLEO Demo Event");
        e.setStartAt(LocalDateTime.now().minusHours(1));
        e.setEndAt(LocalDateTime.now().plusHours(6));
        // default flags already set
        eventRepo.save(e);

        Vendor v1 = new Vendor();
        v1.setEvent(e);
        v1.setName("BRGR");
        v1.setPinPlain("1234");
        v1.setImagePath("/images/brgr.png");
        vendorRepo.save(v1);

        Vendor v2 = new Vendor();
        v2.setEvent(e);
        v2.setName("DESOUKY&SODA");
        v2.setPinPlain("4321");
        v2.setImagePath("/images/desoky-soda.png");
        vendorRepo.save(v2);

        Vendor v3 = new Vendor();
        v3.setEvent(e);
        v3.setName("Koffee Kulture");
        v3.setPinPlain("9876");
        v3.setImagePath("/images/koffee-kulture.jpg");
        vendorRepo.save(v3);

        TierPolicy vipPolicy = new TierPolicy();
        vipPolicy.setEvent(e);
        vipPolicy.setTierCode(TierCode.VIP);
        vipPolicy.setUnlimited(true);
        tierPolicyRepo.save(vipPolicy);

        TierPolicy regPolicy = new TierPolicy();
        regPolicy.setEvent(e);
        regPolicy.setTierCode(TierCode.REG);
        regPolicy.setUnlimited(false);
        regPolicy.setMaxItemsPerVendor(1);
        tierPolicyRepo.save(regPolicy);

        MenuItem m11 = new MenuItem();
        m11.setVendor(v1);
        m11.setName("Smash BRGR");
        m11.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m11);

        MenuItem m12 = new MenuItem();
        m12.setVendor(v1);
        m12.setName("Truffle Fries");
        m12.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m12);

        MenuItem m13 = new MenuItem();
        m13.setVendor(v1);
        m13.setName("Loaded Chicken Strips");
        m13.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m13);

        MenuItem m21 = new MenuItem();
        m21.setVendor(v2);
        m21.setName("Desouky Street Pizza");
        m21.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m21);

        MenuItem m22 = new MenuItem();
        m22.setVendor(v2);
        m22.setName("Creamy Macarona Bechamel");
        m22.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m22);

        MenuItem m23 = new MenuItem();
        m23.setVendor(v2);
        m23.setName("Desouky Liver Sandwich");
        m23.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m23);

        MenuItem m31 = new MenuItem();
        m31.setVendor(v3);
        m31.setName("Signature Cappuccino");
        m31.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m31);

        MenuItem m32 = new MenuItem();
        m32.setVendor(v3);
        m32.setName("Cold Brew Tonic");
        m32.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m32);

        MenuItem m33 = new MenuItem();
        m33.setVendor(v3);
        m33.setName("Hazelnut Latte");
        m33.setPrice(new BigDecimal("0"));
        menuItemRepo.save(m33);

        // Tickets
        Ticket tVip = new Ticket();
        tVip.setEvent(e);
        tVip.setQrCode("VIP-001");
        tVip.setTierCode(TierCode.VIP);
        tVip.setHolderName("VIP Guest");
        tVip.setHolderPhone("01000000001");
        tVip.setSerial("S-VIP-1");
        ticketRepo.save(tVip);

        Ticket tReg = new Ticket();
        tReg.setEvent(e);
        tReg.setQrCode("REG-001");
        tReg.setTierCode(TierCode.REG);
        tReg.setHolderName("REG Guest");
        tReg.setHolderPhone("01000000002");
        tReg.setSerial("S-REG-1");
        ticketRepo.save(tReg);

        Role adminRole = ensureRole("ROLE_ADMIN");
        Role organizerRole = ensureRole("ROLE_ORGANIZER");
        Role vendorRole = ensureRole("ROLE_VENDOR");
        Role staffRole = ensureRole("ROLE_STAFF");
        Role usherRole = ensureRole("ROLE_USHER");

        if (userAccountRepo.findByUsername("admin").isEmpty()){
            UserAccount admin = new UserAccount();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.getRoles().add(adminRole);
            admin.getRoles().add(organizerRole);
            userAccountRepo.save(admin);
        }

        if (userAccountRepo.findByUsername("organizer").isEmpty()){
            UserAccount organizer = new UserAccount();
            organizer.setUsername("organizer");
            organizer.setPassword(passwordEncoder.encode("Organizer@123"));
            organizer.getRoles().add(organizerRole);
            organizer.setEvent(e);
            userAccountRepo.save(organizer);
        }

        if (userAccountRepo.findByUsername("vendor1").isEmpty()){
            UserAccount vendorUser = new UserAccount();
            vendorUser.setUsername("vendor1");
            vendorUser.setPassword(passwordEncoder.encode("Vendor@123"));
            vendorUser.getRoles().add(vendorRole);
            vendorUser.setVendor(v1);
            userAccountRepo.save(vendorUser);
        }

        if (userAccountRepo.findByUsername("staff1").isEmpty()){
            UserAccount staff = new UserAccount();
            staff.setUsername("staff1");
            staff.setPassword(passwordEncoder.encode("Staff@123"));
            staff.getRoles().add(staffRole);
            staff.setVendor(v1);
            userAccountRepo.save(staff);
        }

        createUsherAccountIfMissing("usher_brgr", v1, usherRole);
        createUsherAccountIfMissing("usher_desouky", v2, usherRole);
        createUsherAccountIfMissing("usher_koffee", v3, usherRole);
    }

    private Role ensureRole(String name){
        return roleRepo.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            return roleRepo.save(role);
        });
    }

    private void createUsherAccountIfMissing(String username, Vendor vendor, Role usherRole) {
        if (userAccountRepo.findByUsername(username).isPresent()) {
            return;
        }
        UserAccount usher = new UserAccount();
        usher.setUsername(username);
        usher.setPassword(passwordEncoder.encode("Usher@123"));
        usher.getRoles().add(usherRole);
        usher.setVendor(vendor);
        usher.setEvent(vendor.getEvent());
        userAccountRepo.save(usher);
    }
}
