package com.fbcorp.gleo.web;

import com.fbcorp.gleo.repo.OrderRepo;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public class AdminCleanupController {

    private final OrderRepo orderRepo;

    public AdminCleanupController(OrderRepo orderRepo) {
        this.orderRepo = orderRepo;
    }

    @GetMapping("/cleanup-orders")
    public String cleanupOrdersPage() {
        return "admin/cleanup_orders";
    }

    @PostMapping("/cleanup-orders")
    @ResponseBody
    @Transactional
    public String cleanupAllOrders() {
        long orderCount = orderRepo.count();
        
        orderRepo.deleteAll();
        
        return String.format("Deleted %d orders successfully (order items deleted automatically via cascade)", 
                           orderCount);
    }
}
