package com.fbcorp.gleo.service;

import com.fbcorp.gleo.domain.*;
import com.fbcorp.gleo.repo.OrderRepo;
import com.fbcorp.gleo.repo.TierConsumptionRepo;
import com.fbcorp.gleo.websocket.OrderUpdateMessage;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Service
public class OrderService {
    private final OrderRepo orderRepo;
    private final TierConsumptionRepo tierConsumptionRepo;
    private final EventPolicyService policyService;
    private final SimpMessagingTemplate messagingTemplate;

    public OrderService(OrderRepo orderRepo, 
                       TierConsumptionRepo tierConsumptionRepo, 
                       EventPolicyService policyService,
                       SimpMessagingTemplate messagingTemplate) {
        this.orderRepo = orderRepo;
        this.tierConsumptionRepo = tierConsumptionRepo;
        this.policyService = policyService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void markStatus(Long orderId, OrderStatus status) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        o.setStatus(status);
        orderRepo.save(o);
        
        // Broadcast order update
        broadcastOrderUpdate(o);
    }

    @Transactional
    public void markCompletedByGuest(Long orderId, String deviceHash, String pinLast4) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (o.getStatus() != OrderStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order not READY");
        }
        o.setStatus(OrderStatus.COMPLETED);
        o.setConfirmedByGuest(true);
        if (pinLast4 != null && pinLast4.length() >= 4) {
            o.setConfirmedStaffPinLast4(pinLast4.substring(pinLast4.length()-4));
        }
        orderRepo.save(o);

        // Broadcast order update
        broadcastOrderUpdate(o);

        // Track tier consumption for limited tiers
        Ticket t = o.getTicket();
        TierPolicy tierPolicy = policyService.tierPolicy(o.getEvent().getCode(), t.getTierCode());
        if (tierPolicy.hasLimit()) {
            TierConsumption tc = tierConsumptionRepo
                    .findByEventAndTicketAndVendor(o.getEvent(), t, o.getVendor())
                    .orElseGet(() -> {
                        TierConsumption n = new TierConsumption();
                        n.setEvent(o.getEvent());
                        n.setTicket(t);
                        n.setVendor(o.getVendor());
                        return n;
                    });
            int orderQty = o.getItems().stream().mapToInt(OrderItem::getQty).sum();
            int previous = tc.getTotalItemsConsumed();
            tc.setTotalItemsConsumed(previous + orderQty);
            tierConsumptionRepo.save(tc);
        }
    }

    private void broadcastOrderUpdate(Order order) {
        OrderUpdateMessage message = new OrderUpdateMessage(
            order.getId(),
            order.getEvent().getCode(),
            order.getStatus().toString(),
            order.getTicket().getHolderName(),
            order.getVendor().getName(),
            order.getItems().stream()
                .map(item -> item.getQty() + "x " + item.getMenuItem().getName())
                .collect(Collectors.joining(", "))
        );
        
        messagingTemplate.convertAndSend("/topic/orders/" + order.getEvent().getCode(), message);
    }
}
