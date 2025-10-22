package com.fbcorp.gleo.web;

import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.OrderItem;
import com.fbcorp.gleo.domain.OrderStatus;
import com.fbcorp.gleo.domain.Ticket;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.TicketRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private VendorRepo vendorRepo;

    @Autowired
    private TicketRepo ticketRepo;

    @Autowired
    private MenuItemRepo menuItemRepo;

    @BeforeEach
    void setUpOrder() {
        if (!orderRepo.findAll().isEmpty()) {
            return;
        }
        Vendor vendor = vendorRepo.findAll().stream().findFirst().orElseThrow();
        Ticket ticket = ticketRepo.findByQrCode("VIP-001").orElseThrow();

        Order order = new Order();
        order.setEvent(vendor.getEvent());
        order.setVendor(vendor);
        order.setTicket(ticket);
        order.setStatus(OrderStatus.NEW);

        var firstItem = menuItemRepo.findByVendorAndAvailableTrue(vendor).stream().findFirst().orElseThrow();
        OrderItem orderItem = new OrderItem();
        orderItem.setMenuItem(firstItem);
        orderItem.setQty(1);
        order.addItem(orderItem);

        orderRepo.save(order);
    }

    @Test
    void usherBoardLoads() throws Exception {
        mockMvc.perform(get("/e/G2025/usher"))
                .andExpect(status().isOk())
                .andExpect(view().name("usher_board"))
                .andExpect(model().attributeExists("statusBuckets"))
                .andExpect(model().attribute("hasAnyOrders", true));
    }
}
