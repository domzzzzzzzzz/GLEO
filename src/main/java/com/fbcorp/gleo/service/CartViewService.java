package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.MenuItem;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.MenuItemRepo;
import com.fbcorp.gleo.repo.VendorRepo;
import com.fbcorp.gleo.web.CartSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartViewService {

    private final MenuItemRepo menuItemRepo;
    private final VendorRepo vendorRepo;

    public CartViewService(MenuItemRepo menuItemRepo, VendorRepo vendorRepo) {
        this.menuItemRepo = menuItemRepo;
        this.vendorRepo = vendorRepo;
    }

    public CartSummary summarize(CartSession cartSession) {
        if (cartSession == null || cartSession.isEmpty()) {
            return CartSummary.empty();
        }

        List<VendorGroup> groups = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int totalQty = 0;

        for (var vendorEntry : cartSession.getAll().entrySet()) {
            Long vendorId = vendorEntry.getKey();
            Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
            if (vendor == null) {
                continue;
            }
            List<CartLine> lines = new ArrayList<>();
            BigDecimal vendorTotal = BigDecimal.ZERO;

            for (var itemEntry : vendorEntry.getValue().entrySet()) {
                Long itemId = itemEntry.getKey();
                MenuItem menuItem = menuItemRepo.findById(itemId).orElse(null);
                if (menuItem == null) {
                    continue;
                }
                int qty = Math.max(1, itemEntry.getValue());
                BigDecimal price = menuItem.getPrice() != null ? menuItem.getPrice() : BigDecimal.ZERO;
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
                vendorTotal = vendorTotal.add(subtotal);
                totalQty += qty;
                lines.add(new CartLine(menuItem.getId(), menuItem.getName(), qty, price, subtotal));
            }

            if (!lines.isEmpty()) {
                groups.add(new VendorGroup(vendorId, vendor.getName(), lines, vendorTotal));
                grandTotal = grandTotal.add(vendorTotal);
            }
        }

        if (groups.isEmpty()) {
            return CartSummary.empty();
        }

        return new CartSummary(groups, grandTotal, totalQty);
    }

    public record CartSummary(List<VendorGroup> groups, BigDecimal grandTotal, int totalQty) {
        public static CartSummary empty() {
            return new CartSummary(List.of(), BigDecimal.ZERO, 0);
        }

        public boolean hasItems() {
            return totalQty > 0;
        }

        public String formattedItemCount() {
            if (totalQty == 0) {
                return "Cart is empty";
            }
            return totalQty == 1 ? "1 item" : totalQty + " items";
        }
    }

    public record VendorGroup(Long vendorId, String vendorName, List<CartLine> lines, BigDecimal total) {
        public int lineCount() {
            return lines != null ? lines.size() : 0;
        }
    }

    public record CartLine(Long itemId, String itemName, int qty, BigDecimal price, BigDecimal subtotal) { }
}
