package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.Event;
import com.fbcorp.gleo.domain.Order;
import com.fbcorp.gleo.domain.OrderItem;
import com.fbcorp.gleo.domain.OrderStatus;
import com.fbcorp.gleo.domain.Vendor;
import com.fbcorp.gleo.repo.OrderRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrganizerAnalyticsService {

    private final OrderRepo orderRepo;

    public OrganizerAnalyticsService(OrderRepo orderRepo) {
        this.orderRepo = orderRepo;
    }

    public record VendorStats(Vendor vendor,
                              long totalOrders,
                              long completedOrders,
                              long itemsServed,
                              BigDecimal revenue) { }

    public Map<Long, VendorStats> computeVendorStats(Event event, List<Vendor> vendors) {
        Map<Long, VendorStatsAccumulator> accumulators = new LinkedHashMap<>();
        for (Vendor vendor : vendors) {
            accumulators.put(vendor.getId(), new VendorStatsAccumulator(vendor));
        }

        List<Order> orders = orderRepo.findByEvent(event);
        for (Order order : orders) {
            Vendor vendor = order.getVendor();
            if (vendor == null) {
                continue;
            }
            VendorStatsAccumulator accumulator = accumulators.computeIfAbsent(
                    vendor.getId(), id -> new VendorStatsAccumulator(vendor));
            accumulator.registerOrder(order);
        }
        return accumulators.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toSummary(),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private static class VendorStatsAccumulator {
        private final Vendor vendor;
        private long totalOrders = 0L;
        private long completedOrders = 0L;
        private long itemsServed = 0L;
        private BigDecimal revenue = BigDecimal.ZERO;

        VendorStatsAccumulator(Vendor vendor) {
            this.vendor = vendor;
        }

        void registerOrder(Order order) {
            totalOrders++;
            if (order.getStatus() == OrderStatus.COMPLETED) {
                completedOrders++;
                for (OrderItem item : order.getItems()) {
                    int qty = Math.max(0, item.getQty());
                    itemsServed += qty;
                    BigDecimal price = BigDecimal.ZERO;
                    if (item.getMenuItem() != null && item.getMenuItem().getPrice() != null) {
                        price = item.getMenuItem().getPrice();
                    }
                    revenue = revenue.add(price.multiply(BigDecimal.valueOf(qty)));
                }
            }
        }

        VendorStats toSummary() {
            return new VendorStats(
                    vendor,
                    totalOrders,
                    completedOrders,
                    itemsServed,
                    revenue
            );
        }
    }
}

