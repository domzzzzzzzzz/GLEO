package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.repo.OrderRepo;
import org.springframework.stereotype.Service;

@Service
public class VendorAuthService {
    private final OrderRepo orderRepo;
    public VendorAuthService(OrderRepo orderRepo){ this.orderRepo = orderRepo; }

    // Demo: check last 4 of vendor pin (stored plain in vendor entity via order.vendor)
    public boolean isValidPinForOrder(Long orderId, String pin){
        Order o = orderRepo.findById(orderId).orElse(null);
        if (o == null || o.getVendor().getPinPlain() == null) return false;
        String vpin = o.getVendor().getPinPlain();
        return vpin.equals(pin);
    }
}
